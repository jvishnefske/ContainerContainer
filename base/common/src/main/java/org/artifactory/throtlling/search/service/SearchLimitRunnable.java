package org.artifactory.throtlling.search.service;

import jakarta.servlet.ServletException;

import java.io.IOException;

@FunctionalInterface
public interface SearchLimitRunnable {

    void run() throws ServletException, IOException;
}
