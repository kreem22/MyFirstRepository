package com.dyna;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

public final class DynaCode {

	public interface DynaInvokeRemote {
		Object invoke();
	}

	private String dynaImplClassName = "DynaClass";

	private String compileClasspath;

	private ClassLoader parentClassLoader;

	private Set<SourceDir> sourceDirs = new HashSet<>();

	// class name => LoadedClass
	private HashMap<String, LoadedClass> loadedClasses = new HashMap<>();

	public DynaCode() {
		this(Thread.currentThread().getContextClassLoader());
	}

	public DynaCode(ClassLoader parentClassLoader) {
		this(extractClasspath(parentClassLoader), parentClassLoader);
	}

	/**
	 * @param compileClasspath
	 *            used to compile dynamic classes
	 * @param parentClassLoader
	 *            the parent of the class loader that loads all the dynamic
	 *            classes
	 */
	public DynaCode(String compileClasspath, ClassLoader parentClassLoader) {
		this.compileClasspath = compileClasspath;
		this.parentClassLoader = parentClassLoader;
		addDefaultSourceDir();
	}

	/**
	 * Add a directory that contains the source of dynamic java code.
	 * 
	 * @param srcDir
	 * @return true if the add is successful
	 */
	private void addDefaultSourceDir() {
		synchronized (sourceDirs) {
			for (final SourceDir src : sourceDirs) {
				if (src.srcDir.equals(new SourceDir().srcDir)) {
					return;
				}
			}
			// add new
			SourceDir src = new SourceDir();
			sourceDirs.add(src);

			info("Add source dir " + src.srcDir);
		}
	}

	/**
	 * Add a directory that contains the source of dynamic java code.
	 * 
	 * @param srcDir
	 * @return true if the add is successful
	 */
	public boolean addSourceDir(File srcDir) {

		try {
			srcDir = srcDir.getCanonicalFile();
		} catch (IOException e) {
			// ignore
		}

		synchronized (sourceDirs) {

			// check existence
			for (SourceDir src : sourceDirs) {
				if (src.srcDir.equals(srcDir)) {
					return false;
				}
			}

			// add new
			SourceDir src = new SourceDir(srcDir);
			sourceDirs.add(src);

			info("Add source dir " + srcDir);
		}

		return true;
	}

	/**
	 * Returns the up-to-date dynamic class by name.
	 * 
	 * @param className
	 * @return
	 * @throws ClassNotFoundException
	 *             if source file not found or compilation error
	 */
	public Class<?> loadClass(String className) throws ClassNotFoundException {

		LoadedClass loadedClass = null;
		synchronized (loadedClasses) {
			loadedClass = (LoadedClass) loadedClasses.get(className);
		}

		// first access of a class
		if (loadedClass == null) {

			String resource = className.replace('.', '/') + ".java";
			SourceDir src = locateResource(resource);
			if (src == null) {
				throw new ClassNotFoundException("DynaCode class not found "
						+ className);
			}

			synchronized (this) {

				// compile and load class
				loadedClass = new LoadedClass(className, src);

				synchronized (loadedClasses) {
					loadedClasses.put(className, loadedClass);
				}
			}

			return loadedClass.clazz;
		}

		// subsequent access
		if (loadedClass.isChanged()) {
			// unload and load again
			unload(loadedClass.srcDir);
			return loadClass(className);
		}

		return loadedClass.clazz;
	}

	private SourceDir locateResource(String resource) {
		for (SourceDir src : sourceDirs) {
			if (new File(src.srcDir, resource).exists()) {
				return src;
			}
		}
		return null;
	}

	private void unload(SourceDir src) {
		// clear loaded classes
		synchronized (loadedClasses) {
			for (Iterator<LoadedClass> iter = loadedClasses.values().iterator(); iter
					.hasNext();) {
				LoadedClass loadedClass = (LoadedClass) iter.next();
				if (loadedClass.srcDir == src) {
					iter.remove();
				}
			}
		}

		// create new class loader
		src.recreateClassLoader();
	}

	/**
	 * Get a resource from added source directories.
	 * 
	 * @param resource
	 * @return the resource URL, or null if resource not found
	 */
	public URL getResource(String resource) {
		try {

			SourceDir src = locateResource(resource);
			return src == null ? null : new File(src.srcDir, resource).toURI()
					.toURL();

		} catch (MalformedURLException e) {
			// should not happen
			return null;
		}
	}

	/**
	 * Get a resource stream from added source directories.
	 * 
	 * @param resource
	 * @return the resource stream, or null if resource not found
	 */
	public InputStream getResourceAsStream(String resource) {
		try {

			SourceDir src = locateResource(resource);
			return src == null ? null : new FileInputStream(new File(
					src.srcDir, resource));

		} catch (FileNotFoundException e) {
			// should not happen
			return null;
		}
	}

	/**
	 * Create a proxy instance that implements the specified access interface
	 * and delegates incoming invocations to the specified dynamic
	 * implementation. The dynamic implementation may change at run-time, and
	 * the proxy will always delegates to the up-to-date implementation.
	 * 
	 * @param interfaceClass
	 *            the access interface
	 * @param implClassName
	 *            the backend dynamic implementation
	 * @return
	 * @throws RuntimeException
	 *             if an instance cannot be created, because of class not found
	 *             for example
	 */
	public Object newProxyInstance(Class<?> interfaceClass,
			final String dynaLine, final boolean isVoid)
			throws RuntimeException {
		MyInvocationHandler handler = new MyInvocationHandler(dynaLine, isVoid);
		return Proxy.newProxyInstance(interfaceClass.getClassLoader(),
				new Class[] { interfaceClass }, handler);
	}

	private class SourceDir {
		File srcDir;

		File binDir;

		Javac javac;

		URLClassLoader classLoader;

		SourceDir() {
			this.binDir = new File(System.getProperty("java.io.tmpdir"),
					"dynacode/");
			this.binDir.mkdirs();

			this.srcDir = this.binDir;

			// prepare compiler
			this.javac = new Javac(compileClasspath, binDir.getAbsolutePath());

			// class loader
			recreateClassLoader();
		}

		SourceDir(File srcDir) {
			/* this.srcDir = srcDir; */

			/*
			 * String subdir = srcDir.getAbsolutePath().replace(':', '_')
			 * .replace('/', '_').replace('\\', '_');
			 */
			this.binDir = new File(System.getProperty("java.io.tmpdir"),
					"dynacode/"/* + subdir */);
			this.binDir.mkdirs();

			this.srcDir = this.binDir;

			// prepare compiler
			this.javac = new Javac(compileClasspath, binDir.getAbsolutePath());

			// class loader
			recreateClassLoader();
		}

		void recreateClassLoader() {
			try {
				classLoader = new URLClassLoader(new URL[] { binDir.toURI()
						.toURL() }, parentClassLoader);
			} catch (MalformedURLException e) {
				// should not happen
			}
		}

	}

	private static class LoadedClass {
		String className;

		SourceDir srcDir;

		File srcFile;

		File binFile;

		Class<?> clazz;

		long lastModified;

		LoadedClass(String className, SourceDir src) {
			this.className = className;
			this.srcDir = src;

			String path = className.replace('.', '/');
			this.srcFile = new File(src.srcDir, path + ".java");
			this.binFile = new File(src.binDir, path + ".class");

			compileAndLoadClass();
		}

		boolean isChanged() {
			return srcFile.lastModified() != lastModified;
		}

		void compileAndLoadClass() {

			if (clazz != null) {
				return; // class already loaded
			}

			// compile, if required
			String error = null;
			if (binFile.lastModified() < srcFile.lastModified()) {
				error = srcDir.javac.compile(new File[] { srcFile });
			}

			if (error != null) {
				throw new RuntimeException("Failed to compile "
						+ srcFile.getAbsolutePath() + ". Error: " + error);
			}

			if (!new File(srcDir.binDir + "\\" + className + ".class").exists()) {
				throw new RuntimeException("Failed to compile "
						+ srcFile.getAbsolutePath() + ". Error: " + error);
			}

			try {
				// load class
				clazz = srcDir.classLoader.loadClass(className);

				// load class success, remember timestamp
				lastModified = srcFile.lastModified();

			} catch (ClassNotFoundException e) {
				throw new RuntimeException("Failed to load DynaCode class "
						+ srcFile.getAbsolutePath());
			}

			info("Init " + clazz);
		}
	}

	private class MyInvocationHandler implements InvocationHandler {

		Object backend;

		MyInvocationHandler(final String dynaLine, final boolean isVoid) {
			createInvocationClass(dynaLine, isVoid);
			try {
				Class<?> clz = loadClass(dynaImplClassName);
				backend = newDynaCodeInstance(clz);

			} catch (ClassNotFoundException e) {
				throw new RuntimeException(e);
			}
		}

		private void createInvocationClass(String dynaLine, boolean isVoid) {
			final SourceDir sourceDir = sourceDirs.toArray(new SourceDir[] {})[0];
			final String sourceFile = sourceDir.srcDir + "\\"
					+ dynaImplClassName.replace('.', '/') + ".java";
			try (InputStream is = Thread.currentThread()
					.getContextClassLoader()
					.getResourceAsStream("com/dyna/compiled/DynaFile.dat");
					OutputStream os = new FileOutputStream(new File(sourceFile));
					InputStreamReader isr = new InputStreamReader(is, StandardCharsets.UTF_8);
					OutputStreamWriter osw = new OutputStreamWriter(os, StandardCharsets.UTF_8);
					BufferedReader br = new BufferedReader(isr);
					BufferedWriter bw = new BufferedWriter(osw);
					) {
				if (createSource(br, bw, new File(sourceFile), dynaLine, isVoid) == 0) {
					throw new RuntimeException("File not written");
				}
				sourceDir.recreateClassLoader();
			} catch (Exception e1) {
				throw new RuntimeException(e1);
			}
		}

		private long createSource(final BufferedReader br, final BufferedWriter bw, final File sourceFile,
				final String dynaLine, final boolean isVoid) throws IOException {
			long nread = 0L;
			String line;
			while ((line  = br.readLine()) != null) {
				line = line
						.replaceAll(
								"DynaInvokeRemoteImpl",
								sourceFile.getName().substring(0,
										sourceFile.getName().indexOf(".")));
				if(isVoid){
					line = line.replaceAll("o = [/][*]Method Stub[*][/]", dynaLine);
				}
				else
					line = line.replaceAll("[/][*]Method Stub[*][/]", dynaLine);
									
				bw.write(line + System.getProperty("line.separator"));
				++nread;
			}
			bw.flush();
			return nread;
		}

		public Object invoke(Object proxy, Method method, Object[] args)
				throws Throwable {

			// check if class has been updated
			Class<?> clz = loadClass(dynaImplClassName);
			if (backend.getClass() != clz) {
				backend = newDynaCodeInstance(clz);
			}

			try {
				// invoke on backend
				return method.invoke(backend, args);

			} catch (InvocationTargetException e) {
				throw e.getTargetException();
			}
		}

		private Object newDynaCodeInstance(Class<?> clz) {
			try {
				return clz.newInstance();
			} catch (Exception e) {
				throw new RuntimeException(
						"Failed to new instance of DynaCode class "
								+ clz.getName(), e);
			}
		}

	}

	/**
	 * Extracts a classpath string from a given class loader. Recognizes only
	 * URLClassLoader.
	 */
	private static String extractClasspath(ClassLoader cl) {
		StringBuffer buf = new StringBuffer();

		while (cl != null) {
			if (cl instanceof URLClassLoader) {
				URL urls[] = ((URLClassLoader) cl).getURLs();
				for (int i = 0; i < urls.length; i++) {
					if (buf.length() > 0) {
						buf.append(File.pathSeparatorChar);
					}
					if (System.getProperty("os.name").toLowerCase()
							.contains("win"))
						buf.append(new File(urls[i].getFile())
								.getAbsolutePath());
					else
						buf.append(urls[i].getFile());
				}
			}
			cl = cl.getParent();
		}
		buf.append(File.pathSeparatorChar);
		return buf.toString();
	}

	/**
	 * Log a message.
	 */
	private static void info(String msg) {
		System.out.println("[DynaCode] " + msg);
	}

	private static DynaInvokeRemote getDynaInvokeRemote(final String dynaLine,
			final boolean isVoid) {
		DynaCode dynacode = new DynaCode();
		return (DynaInvokeRemote) dynacode.newProxyInstance(
				DynaInvokeRemote.class, dynaLine, isVoid);
	}

	public static Object invoke(final String dynaLine) {
		try {
			final DynaInvokeRemote dynaInvokeRemote = DynaCode
					.getDynaInvokeRemote(dynaLine, false);
			return dynaInvokeRemote.invoke();
		} catch (Exception e) {
			try {
				final DynaInvokeRemote dynaInvokeRemote = DynaCode
						.getDynaInvokeRemote(dynaLine, true);
				return dynaInvokeRemote.invoke();
			} catch (Exception e1) {
				e1.printStackTrace();
			}
			e.printStackTrace();
		}
		return null;
	}

}