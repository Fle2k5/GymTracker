package com.example.gymtracker.service;

public class AppException extends IllegalArgumentException {
    private final String code;
    private final Object[] arguments;

    public AppException(String code, Object... arguments) {
        super(code);
        this.code = code;
        this.arguments = arguments;
    }

    public String getCode() { return code; }
    public Object[] getArguments() { return arguments; }
}
