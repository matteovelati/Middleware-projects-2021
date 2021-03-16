/**
 * AJAX call management
 */

function makeCall(method, url, elements, cback, reset = true) {
    var req = new XMLHttpRequest(); // visible by closure
    req.onreadystatechange = function() {
        cback(req)
    }; // closure
    req.open(method, url, true);
    if (elements == null) {
        req.send();
    } else {
        req.send(elements);
    }
}

var http_server_address = "172.20.10.2"