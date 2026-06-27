package com.askdb;

import com.askdb.api.ApiServer;

public class Main {
    public static void main(String[] args) throws Exception {
        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "9090"));
        ApiServer server = new ApiServer(port);
        server.start();
    }
}
