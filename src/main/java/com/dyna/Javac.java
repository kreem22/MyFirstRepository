package com.dyna;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * A wrapper to ease the use of com.sun.tools.javac.Main.
 * 
 * @author liyang
 */
public final class Javac {

	private String classpath;

	private String outputdir;

	private String sourcepath;

	private String bootclasspath;

	private String extdirs;

	private String encoding;

	private String target;

	public Javac(String classpath, String outputdir) {
		this.classpath = classpath;
		this.outputdir = outputdir;
	}

	/**
	 * Compile the given source files.
	 * 
	 * @param srcFiles
	 * @return null if success; or compilation errors
	 */
	public String compile(String srcFiles[]) {
		StringWriter err = new StringWriter();
		PrintWriter errPrinter = new PrintWriter(err);
		String args[] = buildJavacArgs(srcFiles);
		for (final String srcFile : srcFiles) {
			try (final InputStream is = new FileInputStream(srcFile);
					final InputStreamReader isr = new InputStreamReader(is, StandardCharsets.UTF_8);
					final BufferedReader reader = new BufferedReader(isr);) {
				System.out.println("Compiling " + srcFile + "...");
				String line;
				while ((line = reader.readLine()) != null) {
					System.out.write(line.getBytes(StandardCharsets.UTF_8));
					System.out.write(System.getProperty("line.separator").getBytes());
					System.out.flush();
				}
			} catch (Exception e) {
			}
		}
		int resultCode = compile(args, errPrinter);

		errPrinter.close();
		return (resultCode == 0) ? null : err.toString();
	}

	public String compile(File srcFiles[]) {
		String paths[] = new String[srcFiles.length];
		for (int i = 0; i < paths.length; i++) {
			paths[i] = srcFiles[i].getAbsolutePath();
		}
		return compile(paths);
	}

	private String[] buildJavacArgs(String srcFiles[]) {
		ArrayList<String> args = new ArrayList<>();
		if (classpath != null) {
			args.add("-cp");
			args.add(classpath);
		}
		if (outputdir != null) {
			args.add("-d");
			args.add(outputdir);
		}
		if (sourcepath != null) {
			args.add("-sourcepath");
			args.add(sourcepath);
		}
		if (bootclasspath != null) {
			args.add("-bootclasspath");
			args.add(bootclasspath);
		}
		if (extdirs != null) {
			args.add("-extdirs");
			args.add(extdirs);
		}
		if (encoding != null) {
			args.add("-encoding");
			args.add(encoding);
		}
		if (target != null) {
			args.add("-target");
			args.add(target);
		}
		for (int i = 0; i < srcFiles.length; i++) {
			args.add(srcFiles[i]);
		}

		return (String[]) args.toArray(new String[args.size()]);
	}

	public String getBootclasspath() {
		return bootclasspath;
	}

	public void setBootclasspath(String bootclasspath) {
		this.bootclasspath = bootclasspath;
	}

	public String getClasspath() {
		return classpath;
	}

	public void setClasspath(String classpath) {
		this.classpath = classpath;
	}

	public String getEncoding() {
		return encoding;
	}

	public void setEncoding(String encoding) {
		this.encoding = encoding;
	}

	public String getExtdirs() {
		return extdirs;
	}

	public void setExtdirs(String extdirs) {
		this.extdirs = extdirs;
	}

	public String getOutputdir() {
		return outputdir;
	}

	public void setOutputdir(String outputdir) {
		this.outputdir = outputdir;
	}

	public String getSourcepath() {
		return sourcepath;
	}

	public void setSourcepath(String sourcepath) {
		this.sourcepath = sourcepath;
	}

	public String getTarget() {
		return target;
	}

	public void setTarget(String target) {
		this.target = target;
	}

	private static File searchToolsDotJar(Set<String> set) {
		if (set == null)
			set = new HashSet<String>(System.getenv().values());
		for (final String d : set) {

			if (d.contains(";")) {
				File tmp = searchToolsDotJar(new HashSet<>(Arrays.asList(d.split(";"))));
				if (tmp != null)
					return tmp;
			}
			File toolsDotJar = new File(d + "/lib/tools.jar");

			if (d.toUpperCase().contains("JDK") && toolsDotJar.exists()) {
				return toolsDotJar;
			}
		}
		return null;
	}

	private static int compile(final String[] args, final PrintWriter pw) {
		final File toolsDotJar = searchToolsDotJar(null);
		if (toolsDotJar != null) {
			ClassLoader cl = null;
			try {
				cl = new URLClassLoader(new URL[] { toolsDotJar.toURI().toURL() });
				final Class<?> jCompiler = cl.loadClass("com.sun.tools.javac.Main");
				return (Integer) jCompiler
						.getDeclaredMethod("compile", new Class[] { String[].class, PrintWriter.class })
						.invoke(null, new Object[] { args, pw });
			} catch (Exception e) {
				e.printStackTrace();
			} finally {

			}
		}
		throw new RuntimeException();
	}

}
