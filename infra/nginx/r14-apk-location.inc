location = /r14-artifacts/hhy-r14-ca57666-debug.apk {
    default_type application/vnd.android.package-archive;
    add_header Content-Disposition 'attachment; filename="hhy-r14-ca57666-debug.apk"' always;
    add_header X-Content-Type-Options nosniff always;
    try_files $uri =404;
}
