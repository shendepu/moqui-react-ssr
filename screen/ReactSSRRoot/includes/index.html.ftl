<!doctype html>
<html>
<head><title>React Redux Starter Kit</title>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width,initial-scale=1">
    <link rel="stylesheet" href="https://maxcdn.bootstrapcdn.com/bootstrap/3.3.6/css/bootstrap.min.css">
    <link rel="shortcut icon" href="/dist/favicon.ico">
    <link href="/dist/${ec.context.cssFileMap['app'].getFileName()}" rel="stylesheet">
</head>
<body>
    <script>
        window.___INITIAL_STATE__ = ${storeState}
    </script>

    <div id="root" style="height: 100%">${content}</div>
    <script type="text/javascript" src="/dist/${ec.context.jsFileMap['vendor'].getFileName()}"></script>
    <script type="text/javascript" src="/dist/${ec.context.jsFileMap['app'].getFileName()}"></script>
</body>
</html>