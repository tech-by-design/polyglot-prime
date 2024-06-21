#!/bin/bash

# Listen for connections and respond
while true; do
  # Using netcat to listen on port 8080 and respond with a HTTP message
  echo -e "HTTP/1.1 200 OK\nContent-Length: 7\nConnection: close\n\nSFTP OK" | nc -l -p 8081 -N
done