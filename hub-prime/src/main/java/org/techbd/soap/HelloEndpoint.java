package org.techbd.soap;

import org.springframework.ws.server.endpoint.annotation.Endpoint;
import org.springframework.ws.server.endpoint.annotation.PayloadRoot;
import org.springframework.ws.server.endpoint.annotation.RequestPayload;
import org.springframework.ws.server.endpoint.annotation.ResponsePayload;
import org.techbd.hello.HelloRequest;
import org.techbd.hello.HelloResponse;

@Endpoint
public class HelloEndpoint {
    private static final String NAMESPACE_URI = "http://techbd.org/hello";

    @PayloadRoot(namespace = NAMESPACE_URI, localPart = "HelloRequest")
    @ResponsePayload
    public HelloResponse sayHello(@RequestPayload HelloRequest request) {
        HelloResponse response = new HelloResponse();
        response.setMessage("Hello, " + request.getName() + "!");
        return response;
    }
}