location = /r16-artifacts/hhy-r16-4debca1-debug.apk {
    default_type application/vnd.android.package-archive;
    add_header Content-Disposition "attachment; filename=hhy-r16-4debca1-debug.apk" always;
    add_header X-Content-Type-Options nosniff always;
    add_header Cache-Control "public, max-age=300" always;
    try_files $uri =404;
}
