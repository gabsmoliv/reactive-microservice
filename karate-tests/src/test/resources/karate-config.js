function fn() {
  return {
    authUrl: karate.properties['auth.url'] || 'http://localhost:8081',
    taskUrl: karate.properties['task.url'] || 'http://localhost:8082'
  };
}
