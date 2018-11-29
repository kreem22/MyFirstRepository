package com.postman;

import java.io.BufferedReader;
import java.io.InputStreamReader;

import com.dyna.DynaCode;

public class Client {
	public static void main(String[] args) {
		java.lang.System.out.println(java.lang.String.valueOf("Hello World")
				.split(" ")[java.lang.Integer.parseInt("1")]);
		try {
			BufferedReader sysin = new BufferedReader(new InputStreamReader(
					System.in));

			while (true) {
				System.out.print("Enter a line: ");
				String dynaLine = sysin.readLine();
				System.out.println(DynaCode.invoke(dynaLine));
			}
		} catch (Exception e) {

		}
	}
}
