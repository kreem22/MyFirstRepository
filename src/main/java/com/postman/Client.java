package com.postman;

import java.io.BufferedReader;
import java.io.InputStreamReader;

import com.dyna.DynaCode;

public class Client {
	public static void main(String[] args) {
		try {
			BufferedReader sysin = new BufferedReader(new InputStreamReader(
					System.in));

			while (true) {
				System.out.println("Enter a line: ");
				String dynaLine = sysin.readLine();
				Object o = DynaCode.invoke(dynaLine);
				if(o != null)
					System.out.println(o);
			}
		} catch (Exception e) {

		}
	}
}
