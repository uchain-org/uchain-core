package com.uchain.core.consensus;

public class UnExpectedError extends RuntimeException {
	private static final long serialVersionUID = 1L;
	private String message;

	public UnExpectedError(String message) {
		super();
		this.message = message;
	}
	
}
