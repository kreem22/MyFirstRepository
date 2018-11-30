package com.dyna.client;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.Reader;

import com.dyna.DynaCode;

public class Client {
	public static void main(String[] args) {
		try (Reader r = new InputStreamReader(System.in); BufferedReader sysin = new BufferedReader(r)) {
			while (true) {
				System.out.print("Enter a line: ");
				final String dynaLine = sysin.readLine();
				final Object o = DynaCode.invoke(dynaLine);
				if (o != null)
					System.out.println(o);
			}
		} catch (Exception e) {

		}
	}
}
