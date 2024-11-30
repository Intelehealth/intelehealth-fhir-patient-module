package org.ih.patient.data.exchange.utils;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.TimeUnit;

public class DateUtils {

	public static Date strToDate(String format, String date) throws ParseException {
		return new SimpleDateFormat(format).parse(date);
	}

	public static Date toDate(String date) throws ParseException {
		return strToDate("yyyy-MM-dd", date);
	}

	public static String toFormattedDateNow(String format) {
		return new SimpleDateFormat(format).format(new Date());
	}
	
	public static String toFormattedDateNow() {
		return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
				.format(new Date(System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(2)));
	}
	
	
	public static void main(String[] args) {
		System.out.println(toFormattedDateNow("yyyy-MM-dd HH:mm:ss"));
	}
}
