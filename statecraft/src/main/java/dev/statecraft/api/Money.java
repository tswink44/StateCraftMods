package dev.statecraft.api;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Locale;

public final class Money {
    public static final long MAX = 9_000_000_000_000_000L;

    private Money() {}

    public static long parse(String text) {
        if (text == null || !text.matches("[0-9]{1,14}(\\.[0-9]{1,2})?")) {
            throw new UserError("Enter a non-negative amount with at most two decimal places.");
        }
        try {
            long cents = new BigDecimal(text).movePointRight(2).longValueExact();
            nonNegative(cents);
            return cents;
        } catch (ArithmeticException e) {
            throw new UserError("The amount is too large.");
        }
    }

    public static long positive(long cents) {
        if (cents <= 0 || cents > MAX) {
            throw new UserError("The amount must be positive and no greater than " + format(MAX) + ".");
        }
        return cents;
    }

    public static long nonNegative(long cents) {
        if (cents < 0 || cents > MAX) {
            throw new UserError("The amount is outside the supported range.");
        }
        return cents;
    }

    public static long add(long first, long second) {
        try {
            return nonNegative(Math.addExact(nonNegative(first), nonNegative(second)));
        } catch (ArithmeticException e) {
            throw new UserError("The amount is too large.");
        }
    }

    public static long multiply(long cents, long quantity) {
        if (quantity < 0) {
            throw new UserError("Quantity cannot be negative.");
        }
        try {
            return nonNegative(Math.multiplyExact(nonNegative(cents), quantity));
        } catch (ArithmeticException e) {
            throw new UserError("The amount is too large.");
        }
    }

    public static long tax(long cents, int basisPoints) {
        nonNegative(cents);
        if (basisPoints < 0 || basisPoints > 10_000) {
            throw new UserError("Rates must be between 0 and 10000 basis points (0-100%).");
        }
        return BigInteger.valueOf(cents).multiply(BigInteger.valueOf(basisPoints))
                .divide(BigInteger.valueOf(10_000)).longValueExact();
    }

    public static String format(long cents) {
        return String.format(Locale.ROOT, "$%,.2f", BigDecimal.valueOf(cents, 2));
    }
}
