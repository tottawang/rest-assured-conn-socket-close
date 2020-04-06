package com.sample.conf;

import java.sql.Timestamp;
import java.util.Date;

public class TestLog {

  public static void log(String message) {
    System.out.println("[" + new Timestamp(new Date().getTime()).toString() + "] " + message);
  }

}
