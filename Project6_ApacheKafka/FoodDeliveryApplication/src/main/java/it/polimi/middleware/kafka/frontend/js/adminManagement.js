
(function(){

    var modifyItems, newItem,
        pageOrchestrator = new PageOrchestrator();

    window.addEventListener("load", () => {
        pageOrchestrator.start();
    }, false);

    function ModifyProducts(btnModify, contProducts){
        this.btnModify = btnModify;
        this.contProducts = contProducts;

        this.btnModify.addEventListener("click", (e) =>{
            this.refresh()
        }, false);

        this.refresh = function (){
            document.getElementById("messages").textContent = "";
            var self = this;
            var url = "http://" + http_server_address + ":8500/orderService"
            makeCall("GET", url, null,
                function(req) {
                    if (req.readyState == XMLHttpRequest.DONE) {
                        var message = req.responseText;
                        switch (req.status) {
                            case 200:
                                var productsToShow = JSON.parse(message);
                                self.showItems(productsToShow);
                                break;
                            case 400: // bad request
                                document.getElementById("messages").textContent = "Error adding new product";
                                break;
                        }
                    }
                }
            );
        }

        this.showItems = function (itemsToShow) {
            this.contProducts.innerHTML = "";
            var row, p_name, p_qty, btn_cell, btn;
            row = document.createElement("tr");
            p_name = document.createElement("th");
            p_name.textContent = "Product";
            p_qty = document.createElement("th");
            p_qty.textContent = "Quantity";
            row.appendChild(p_name);
            row.appendChild(p_qty)
            this.contProducts.appendChild(row);
            var keys = Object.keys(itemsToShow);
            var self = this;
            keys.forEach(function(key){
                row = document.createElement("tr");
                p_name = document.createElement("td");
                p_name.textContent = key;
                p_qty = document.createElement("td");
                p_qty.textContent = itemsToShow[key];
                p_qty.setAttribute("contenteditable", "true");
                p_qty.setAttribute("id", key);
                row.appendChild(p_name);
                row.appendChild(p_qty);
                btn_cell = document.createElement("td");
                btn = document.createElement("button");
                btn.setAttribute('type', 'button');
                btn.setAttribute('class', 'btn btn-secondary');
                btn.textContent = "Update product";
                btn_cell.appendChild(btn)
                row.appendChild(btn_cell)
                btn.addEventListener("click", (e) =>{
                    var postElements = "";
                    postElements = key + "=" + document.getElementById(key).textContent
                    var self = this;
                    var url = "http://" + http_server_address + ":8500/orderService"
                    makeCall("POST", url, postElements,
                        function(req) {
                            if (req.readyState == XMLHttpRequest.DONE) {
                                var message = req.responseText;
                                switch (req.status) {
                                    case 200:
                                        document.getElementById("messages").textContent = "Product updated";
                                        break;
                                    case 400: // bad request
                                        document.getElementById("messages").textContent = "Error updating product '" + key + "'";
                                        break;
                                }
                            }
                        }
                    );
                })
                self.contProducts.appendChild(row);
            })

        }
    }

    function NewItem(btnNewItem){
        this.btnNewItem = btnNewItem;

        this.btnNewItem.addEventListener("click", (e) =>{
            var form = e.target.closest("form");
            var element = form.product_name.value + "=" + form.product_qty.value;
            var url = "http://" + http_server_address + ":8500/orderService"
            makeCall("POST", url, element,
                function(req) {
                    if (req.readyState == XMLHttpRequest.DONE) {
                        var message = req.responseText;
                        switch (req.status) {
                            case 200:
                                form.reset();
                                document.getElementById("errorLog").textContent = "Product successfully added.";
                                modifyItems.btnModify.click();
                                break;
                            case 400: // bad request
                                document.getElementById("errorLog").textContent = "Error adding new product";
                                break;
                        }
                    }
                }
            );
        }, false)
    }


    function PageOrchestrator() {
        this.start = function(){
            newItem = new NewItem(
                document.getElementById("addProductBtn")
            );
            modifyItems = new ModifyProducts(
                document.getElementById("refreshItems"),
                document.getElementById("contItems")
            );

            modifyItems.refresh();

        }
    }
})();
