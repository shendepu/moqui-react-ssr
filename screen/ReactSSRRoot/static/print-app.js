println('----- check polyfills')
println(window.fetch)
println(typeof shutdown)
println(setTimeout)
println(clearTimeout)
println(setInterval)
println(setInterval)
println(eventLoop)
println(main)
println(typeof XMLHttpRequest)

setTimeout(function() {
  window.println('test setTimeout successfully')
}, 200)

function renderServer () {
  return app['render']()
}

function getState () {
   return app['getState']()
}
