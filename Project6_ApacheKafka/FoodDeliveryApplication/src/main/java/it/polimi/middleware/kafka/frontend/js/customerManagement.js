
(function(){

    var personalUsername, showOrders, newOrder,
        pageOrchestrator = new PageOrchestrator();

    window.addEventListener("load", () => {
        if(sessionStorage.getItem("username") == null){
            window.location.href = "index.html";
        } else{
            pageOrchestrator.start();
            pageOrchestrator.refresh();
        }
    }, false);

    function PersonalUsername(_username, usernameContainer){
        this.username = _username;
        this.show = function(){
            usernameContainer.textContent = "Welcome back, " + this.username;
        }
    }

    function ShowOrders(username, btnShowOrder, tableContainer){
        this.username = username;
        this.btnShowOrder = btnShowOrder;
        this.tableContainer = tableContainer;

        this.btnShowOrder.addEventListener("click", (e) =>{
            this.refresh();
        }, false);

        this.refresh = function (){
            var self = this;
            var url = "http://" + http_server_address + ":8600/userService?username=" + this.username;
            makeCall("GET", url, null,
                function(req) {
                    if (req.readyState == XMLHttpRequest.DONE) {
                        var message = req.responseText;
                        switch (req.status) {
                            case 200:
                                var ordersToShow = JSON.parse(message);
                                self.showOrders(ordersToShow);
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
                });
        }

        this.showOrders = function (ordersToShow){
            this.tableContainer.innerHTML = "";
            var row, order_id, order_status;
            row = document.createElement("tr");
            order_id = document.createElement("th");
            order_id.textContent = "Order id";
            order_status = document.createElement("th");
            order_status.textContent = "Order Status";
            row.appendChild(order_id);
            row.appendChild(order_status);
            this.tableContainer.appendChild(row);
            var self = this;
            var sortByID = ordersToShow.slice(0);
            sortByID.sort(function(a,b) {
                return a.id - b.id;
            });
            sortByID.forEach(function(order){
                row = document.createElement("tr");
                order_id = document.createElement("td");
                order_id.textContent = order.id;
                row.appendChild(order_id);
                order_status = document.createElement("td");
                order_status.textContent = order.status;
                row.appendChild(order_status);
                self.tableContainer.appendChild(row);
            })
        }

    }

    function NewOrder(username, btnNewOrder, formContainer, addressForm, submitBtn){
        this.username = username;
        this.btnNewOrder = btnNewOrder;
        this.formContainer = formContainer;
        this.addressForm = addressForm;
        this.submitBtn = submitBtn;
        this.newOrderShown = false;
        this.addressForm.style.display = "none";

        this.btnNewOrder.addEventListener("click", (e) => {
            if (this.newOrderShown){
                this.newOrderShown = false;
                this.btnNewOrder.textContent = "Create new order";
                document.getElementById("messages").textContent = "";
                this.formContainer.innerHTML = "";
                this.addressForm.style.display = "none";
            }
            else{
                var self = this;
                var url = "http://" + http_server_address + ":8500/orderService"
                makeCall("GET", url, null,
                    function(req) {
                        if (req.readyState == XMLHttpRequest.DONE) {
                            var message = req.responseText;
                            switch (req.status) {
                                case 200:
                                    var itemsToShow = JSON.parse(message);
                                    self.showItems(itemsToShow);
                                    break;
                                case 400: // bad request
                                    document.getElementById("messages").textContent = "Error retrieving orders";
                                    break;
                                case 401: // unauthorized
                                    document.getElementById("messages").textContent = "Invalid Credentials";
                                    break;
                            }
                        }
                    });
            }
        }, false);

        this.showItems = function (itemsToShow){
            this.newOrderShown = true;
            this.addressForm.style.display = "block";
            this.btnNewOrder.textContent = "Close Order";
            this.formContainer.innerHTML = "";
            var row, item, item_qty, add_title, address, btn;
            row = document.createElement("tr");
            item = document.createElement("th");
            item.textContent = "Products";
            item_qty = document.createElement("th");
            item_qty.textContent = "Quantity";
            row.appendChild(item);
            row.appendChild(item_qty);
            this.formContainer.appendChild(row);
            var keys = Object.keys(itemsToShow);
            var self = this;
            keys.forEach(function(key){
                row = document.createElement("tr");
                item = document.createElement("td");
                item.textContent = key;
                item_qty = document.createElement("td");
                item_qty.textContent = 0;
                item_qty.setAttribute("contenteditable", "true");
                item_qty.setAttribute("id", key);
                row.appendChild(item);
                row.appendChild(item_qty);
                self.formContainer.appendChild(row);
            })
            btn = this.submitBtn.cloneNode(true);
            this.submitBtn.parentNode.replaceChild(btn, this.submitBtn);
            this.submitBtn = btn;
            this.submitBtn.addEventListener("click", (e) =>{
                var form = e.target.closest("form");
                var order = {};
                order.id = null;
                order.username = sessionStorage.getItem('username');
                order.status = null;
                if (form.address.value === "null"){
                    order.address = null;
                }else{
                    order.address = form.address.value;
                }
                order.mapItemToQuantity = {}
                keys.forEach(function(key){
                    if (document.getElementById(key).textContent !== "0") {
                        order.mapItemToQuantity[key] = document.getElementById(key).textContent;
                    }
                })
                var self = this;
                var url = "http://" + http_server_address + ":8600/userService"
                makeCall("POST", url, JSON.stringify(order),
                    function(req) {
                        if (req.readyState == XMLHttpRequest.DONE) {
                            var message = req.responseText;
                            switch (req.status) {
                                case 200:
                                    document.getElementById("messages").textContent = "Order created!"
                                    showOrders.btnShowOrder.click();
                                    PageOrchestrator.refresh();
                                    break;
                                case 400: // bad request
                                    document.getElementById("messages").textContent = "Error creating order"
                                    break;
                                case 401: // unauthorized
                                    document.getElementById("messages").textContent = "Error creating order"
                                    break;
                            }
                        }
                    });

            })
        }
    }


    function PageOrchestrator() {
        this.start = function(){
            personalUsername = new PersonalUsername(sessionStorage.getItem('username'),
                document.getElementById("id_username"));
            showOrders = new ShowOrders(
                sessionStorage.getItem('username'),
                document.getElementById("showOrders"),
                document.getElementById("table-content"));
            newOrder = new NewOrder(
                sessionStorage.getItem('username'),
                document.getElementById("createOrder"),
                document.getElementById("form-content"),
                document.getElementById("newOrder"),
                document.getElementById("submitOrderBtn")
            )

        }

        this.refresh = function(){
            personalUsername.show();
            showOrders.refresh();
        }
    }
})();
