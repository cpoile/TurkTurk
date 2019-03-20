$.ajax({
    type: "GET",
    url: "http://chdp.usask.ca:5000/exp-condition",
    data: {basic: "T0tGYzhxQ0piTXljNzg1VG04Zk9Wc1JsanpQQTlwOkl5T3ZTNzhFWVdpRmR3S0c0eHpHTjBpUXNaQ1hpNU9adzZ0SjdFMkU", nsid: "chp201"},
    dataType: "jsonp",
    success: function(res) {skewer.log(res.site + "?bnNpZA=" + res.bnNpZA);}
});
