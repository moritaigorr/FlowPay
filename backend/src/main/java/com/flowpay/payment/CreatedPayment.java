package com.flowpay.payment;

public record CreatedPayment(int httpStatus, String location, String responseJson, boolean replayed) {}
