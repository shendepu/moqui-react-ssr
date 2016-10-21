<html>
<head>
    <title>Hello React</title>
    <script type="text/javascript" src="/apps/react-ssr/resources/static/vendor/react.js"></script>
    <script type="text/javascript" src="/apps/react-ssr/resources/static/vendor/showdown.min.js"></script>
    <script type="text/javascript" src="http://code.jquery.com/jquery-1.10.0.min.js"></script>
</head>
<body>
<div id="content">${content}</div>
<script type="text/javascript" src="/apps/react-ssr/resources/static/commentBox.js"></script>
<script type="text/javascript">
    $(function () {
        renderClient(${data});
    });
</script>
</body>
</html>