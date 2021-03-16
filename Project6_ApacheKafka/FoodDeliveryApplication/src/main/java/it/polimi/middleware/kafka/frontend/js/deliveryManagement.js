/**
 * Delivery page management
 */

(function() { // avoid variables ending up in the global scope

    var url = "http://" + http_server_address + ":8400/shippingService";

    //inizialization
    window.addEventListener("load", () => {
        document.getElementById("refreshBtn").click();
    }, false);

    document.getElementById("refreshBtn").addEventListener('click', (e) => {
        document.getElementById("errorLog").innerHTML = "";
        makeCall("GET", url, null,
            function(req) {
                if (req.readyState == XMLHttpRequest.DONE) {
                    switch (req.status) {
                        case 200:
                            populateTable(JSON.parse(req.responseText));
                            break;
                        default:
                            document.getElementById("errorLog").textContent = "Error GET request";
                            break;
                    }
                }
            }
        );
    });

    function populateTable(ordersList) {
        var row, orderIdCell, usernameCell, addressCell, statusCell, buttonCell, buttonDeliver;
        var ordersContainer = document.getElementById("ordersContainer");
        ordersContainer.innerHTML = "";

        row = document.createElement("tr");
        orderIdCell = document.createElement("th");
        orderIdCell.textContent = "Order #ID";
        row.appendChild(orderIdCell);

        usernameCell = document.createElement("th");
        usernameCell.textContent = "Username";
        row.appendChild(usernameCell);

        addressCell = document.createElement("th");
        addressCell.textContent = "Address";
        row.appendChild(addressCell);

        statusCell = document.createElement("th");
        statusCell.textContent = "Status";
        row.appendChild(statusCell);

        buttonCell = document.createElement("th");
        buttonCell.textContent = "Actions";
        row.appendChild(buttonCell);

        ordersContainer.appendChild(row);

        var sortByID = ordersList.slice(0);
        sortByID.sort(function(a,b) {
            return a.id - b.id;
        });
        sortByID.forEach(function(order){
            row = document.createElement("tr");

            orderIdCell = document.createElement("td");
            orderIdCell.textContent = order.id;
            row.appendChild(orderIdCell);

            usernameCell = document.createElement("td");
            usernameCell.textContent = order.username;
            row.appendChild(usernameCell);

            addressCell = document.createElement("td");
            addressCell.textContent = order.address;
            row.appendChild(addressCell);

            statusCell = document.createElement("td");
            statusCell.textContent = order.status;
            row.appendChild(statusCell);

            buttonCell = document.createElement("td");
            buttonDeliver = document.createElement("button");
            buttonDeliver.setAttribute("id", order.id);
            buttonDeliver.textContent = "DELIVER";
            buttonDeliver.setAttribute('type', 'button');
            buttonDeliver.setAttribute('class', 'btn btn-secondary');
            buttonDeliver.addEventListener('click', (e) => {
                document.getElementById("errorLog").innerHTML = "";
                var elements = "orderID=" + e.target.getAttribute("id");
                makeCall("POST", url, elements,
                    function(req) {
                        if (req.readyState == XMLHttpRequest.DONE) {
                            switch (req.status) {
                                case 200:
                                    document.getElementById(order.id).style.visibility = "hidden";
                                    document.getElementById("refreshBtn").click();
                                    break;
                                case 400:
                                    document.getElementById("errorLog").textContent = "Error 400 POST";
                                    break;
                                case 501: // bad request
                                    document.getElementById("errorLog").textContent = "Error 501 POST";
                                    break;
                            }
                        }
                    }
                );
            });

            buttonCell.appendChild(buttonDeliver);
            row.appendChild(buttonCell);
            ordersContainer.appendChild(row);
        })
    }

    function sortFunction(a, b) {
        if (a[0] === b[0]) {
            return 0;
        }
        else {
            return (a[0] < b[0]) ? -1 : 1;
        }
    }

})();
