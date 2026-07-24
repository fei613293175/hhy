location = /r09-candidate-media/attempt-2/r11-team-logo.png {
    default_type image/png;
    add_header X-Content-Type-Options nosniff always;
    add_header Cache-Control "public, max-age=300" always;
    try_files $uri =404;
}
