package com.dp4j.samples;
/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

@com.dp4j.Singleton
public class MyBooks {

    private int year; //fields
    private String title;
    private String author;

    public void hello() {
        if(year == Integer.MAX_VALUE)
            System.out.println(instance);
    }
}