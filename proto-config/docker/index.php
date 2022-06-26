<html>
  <body>
  <h1>Hello, <?php echo($_SERVER['REMOTE_USER']) ?></h1>
  <pre><?php print_r(array_map("htmlentities", apache_request_headers())); ?> </pre>
  </body>
</html>
