/**
 * Index page management
 */

(function() { // avoid variables ending up in the global scope

    document.getElementById("loginButton").addEventListener('click', (e) => {
        var form = e.target.closest("form");
        var url = "http://" + http_server_address + ":8600/userLogin?user=" + form.username.value +"&password=" + form.password.value;
        if (form.checkValidity()) {
            makeCall("GET", url, null,
                function(req) {
                    if (req.readyState == XMLHttpRequest.DONE) {
                        var message = req.responseText;
                        switch (req.status) {
                            case 200:
                                sessionStorage.setItem('username', form.username.value);
                                window.location.href = "customer.html";
                                break;
                            case 400: // bad request
                                document.getElementById("errorLog").textContent = message;
                                break;
                            case 401: // unauthorized
                                document.getElementById("errorLog").textContent = "Invalid Credentials";
                                break;
                            case 500: // server error
                                document.getElelemtById("id_alert").textContent = message;
                                break;
                        }
                    }
                }
            );
        } else {
            form.reportValidity();
        }
    });

    document.getElementById("registrationButton").addEventListener('click', (e) => {
        var form = e.target.closest("form");
        var url = "http://" + http_server_address + ":8600/userLogin";
        var elements = "username=" + form.r_username.value + "&password="+ form.r_password.value + "&address=" + form.address.value;

        if (form.checkValidity()) {
            makeCall("POST", url, elements,
                function(req) {
                    if (req.readyState == XMLHttpRequest.DONE) {
                        var message = req.responseText;
                        switch (req.status) {
                            case 200:
                                document.getElementById("errorReg").textContent = "Successful registration";
                                break;
                            case 400: // bad request
                                document.getElementById("errorReg").textContent = "Invalid parameters";
                                break;
                        }
                    }
                }
            );
        } else {
            form.reportValidity();
        }
    });

    document.getElementById("adminButton").addEventListener('click', (e) => {
        window.location.href = "admin.html";
    });

    document.getElementById("deliveryButton").addEventListener('click', (e) => {
        window.location.href = "delivery.html";
    });
})();
