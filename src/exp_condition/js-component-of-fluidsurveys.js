/*global console, jQuery, $, _, Backbone */
// to run: run-skewer, then in the page we want to skewer, click the skewer bookmarklet. now we're connected.
// C-x C-e eval previous form
// C-M-x eval top-level-form
// C-c C-z jump to repl
//
// see also the keybindings file
//

// This is the code to send an NSID from a start page and get back the INTRO page.
$(function(){
    var id = "{{ nsid }}";

    var submitRedirect = function() {
        location.replace("//chp3.usask.ca/pow/exp-condition?basic=iTXljNzg1VG04Zk9Wc1JsanpQQTlwOkl5T3ZTNzhFWVdpRmR3S0c0eHpHT&id=" + id);
    };

    $('#next-submit-button').hide();
    var myButton = $('<input id="my-submit-button" type="button" name="mySubmitButton" class="button" value="Next" style="width: 60px;">');
    $('#next-submit-button').after(myButton);

    myButton.click(function(e){
        submitRedirect();
    });
});



alert("hi");
console.log("test");

// utility to prinnt out an object, or can use skewer.log()
var printObj = function(obj) {
    return (JSON.stringify(obj));
};

$.ajax({
    type: "GET",
    url: "http://chp3.usask.ca:5000/finished-exp",
    data: {pk: "test123"},
    dataType: "jsonp",
    jsonp: "jsonp",
    success: function(res) { skewer.log(res); },
    error: function(res) { skewer.log ("error: "+res);}
});

// to find input box without knowing the id#
$(":text").;
$($(":text")[1]).attr("id");
(parseInt($($(":text")[0]).val()) + parseInt($($(":text")[1]).val()) == 500);

skewer.log($("#survey>#survey-form>.question:first").attr("id"));
$("#survey>#survey-form>.question:first").children(".question-body");

$("#survey-form>.question.text-response").attr("id");
$("#survey-form>.question.text-response :text").val();
$(":text").attr("id");
$("#survey-form>.question.text-response>div.question-body").attr("class");
$("#survey-form>.question.text-response>div.question-body :text").attr("id");
$("#survey-form>.question.section-separator").attr("id");

$("#survey-form>.question.text-response-grid").first().children("div.question-body").attr("class");
$("#survey-form>.question.text-response-grid").first().next().children("div.question-body").attr("class");
parseInt($("#survey-form>.question.hidden-field").first().find(":text").val());

$("#survey-form>.question.hidden-field").first().find(":text").attr("id");
$("#survey-form>.question.hidden-field").first().next().find(":text").attr("id");

$("#survey-form>.question.hidden-field :text").first().next().attr("id");
    .first().next().children("div.question-body").attr("class");


$.ajax({
    type: "GET",
    url: "http://chp3.usask.ca:5000/exp-condition",
    data: {pk: "abcdefghijklmnop"},
    dataType: "jsonp",
    jsonp: "jsonp",
    success: function(res) { skewer.log(res.site); skewer.log(res.site); },
    error: function(res) {skewer.log("error: " + res);}
});

////////////////////////////////////////////////////////////////////////////////
// to make a jsonp request with a timeout that will throw an error/do something.
//
var getJSONP = function(url, data, success, error) {
    var timeOut = setTimeout(function() {
        error();
    }, 3000);

    $.ajax({
        type: "GET",
        url: url,
        data: data,
        dataType: "jsonp",
        jsonp: "jsonp",
        success: function(res){ clearTimeout(timeOut); success(res); }
    });
};

getJSONP("http://chp3.usask.ca:5000/exp-condition", {pk: "abcdefghijklmnop"}, function(res) { skewer.log(res.site); skewer.log(res.site); }, function() {skewer.log("error: no response");});

////////////////////////////////////////////////////////////////////////////////
// the minimum needed for a cross-site request is:
//
// CLOJURE-SIDE: 1. generate-string -- (to turn a clojure map into
// json formatted string ready to be sent), 2. response -- turn the
// string into a map, with body and 200 header, 3. wrap the body in
// the jsonp/callback function name that jQuery gave us. Then jQuery
// gets the body, runs it as a script, and returns the json object as
// data.
//
// JS-SIDE: 1. jsonp=? parameter, or the full ajax object with the
// jsonp property. jQuery replaces the ? with its generated function
// name, which the clojure-side uses to wrap the json response. 2. any
// data params you want to send. That's it.
//
// eg:
//
$.getJSON("http://chp3.usask.ca:5000/test?jsonp=?", {myid: 'id2', args2: 'not cross origin, not js response type'}, function(data){
    skewer.log(data);
});
//
// or the more bare-metal:
//
jQuery.ajax({
    type: "GET",
    url: "http://chp3.usask.ca:5000/test",
    data: {myid: "testid",
           args2: "not cross origin, not js response type"},
    dataType: "jsonp",
    jsonp: "jsonp",
    success: function(res) { skewer.log(res);}
});


$("#survey>#survey-form>.question:first").attr("id");

$(function(){
    var pk = "{{ get_variable.pk }}";

    $('#next-submit-button').hide();
    var myButton = $('<input id="my-submit-button" type="button" name="mySubmitButton" class="button" value="Next" style="width: 60px;">');
    $('#next-submit-button').after(myButton);


    var sendAjax = function() {
        $.ajax({
            type: "GET",
            url: "http://chp3.usask.ca:5000/finished-exp",
            data: {pk: pk},
            dataType: "jsonp",
            jsonp: "jsonp",
            success: function(res) {
                var infoLine = $('<div class="varerror"><ul class="errorlist"><li>' +
                                 'Your completion code is ' + res.code +
                                 'please copy this down now because it will disappear when you leave this page.' +
                                 '</li></ul></div>');
                $("#survey-form > div:first").append(infoLine);
                alert('Your completion code is ' + res.code +
                      'please copy this down now because it will disappear when you leave this page.');
                myButton.attr("disabled","disabled");
                var myFinishedButton = $('<input id="my-finished-button" type="button" name="myFinishedButton" class="button" value="Finished" style="width: 80px;">');
                myButton.after(myFinishedButton);
                myFinishedButton.click(function(e){
                    $('#next-submit-button').click();
                });
            },
            error: function() {alert("There seems to be a problem, probably your unique participant key was lost somewhere along the way. Please return to the Mechanical Turk page and start again. Don't change the url in your browser manually. Thank you!");}
        });
    };

    myButton.click(function(e){
        sendAjax();
    });
});


// participant has finished, send the result to the server.
$(function(){
    var partid = "{{ get_variable.partid }}";

    var sendAjax = function() {
        $.ajax({
            type: "GET",
            url: "http://chp3.usask.ca:5000/finished-exp",
            data: {basic: "T0tGYzhxQ0piTXljNzg1VG04Zk9Wc1JsanpQQTlwOkl5T3ZTNzhFWVdpRmR3S0c0eHpHTjBpUXNaQ1hpNU9adzZ0SjdFMkU", id: partid},
            dataType: "text",
            success: function(res) {console.log("received: " + res); $('#next-submit-button').click();}
        });
    };

    $('#next-submit-button').hide();
    var myButton = $('<input id="my-submit-button" type="button" name="mySubmitButton" class="button" value="Next" style="width: 60px;">');
    $('#next-submit-button').after(myButton);

    myButton.click(function(e){
        sendAjax();
    });
});

var testAjax = function() { $.ajax({
    type: "GET",
    url: "http://chp3.usask.ca:5000/finished-exp",
    data: {basic: "T0tGYzhxQ0piTXljNzg1VG04Zk9Wc1JsanpQQTlwOkl5T3ZTNzhFWVdpRmR3S0c0eHpHTjBpUXNaQ1hpNU9adzZ0SjdFMkU", id: "test5"},
    dataType: "text",
    success: function(res) { alert(res); }
});};

// to load jquery in a skewer buffer:
(function() {
    // Load the script
    var script = document.createElement("SCRIPT");
    script.src = 'https://ajax.googleapis.com/ajax/libs/jquery/1.8.3/jquery.js';
    script.type = 'text/javascript';

    var script2 = document.createElement("SCRIPT");
    script2.src = 'https://ajax.googleapis.com/ajax/libs/jqueryui/1.10.3/jquery-ui.min.js';
    script2.type = 'text/javascript';

    document.getElementsByTagName("head")[0].appendChild(script);
    document.getElementsByTagName("head")[0].appendChild(script2);

    // Poll for jQuery to come into existance
    var checkReady = function(callback) {
        if (window.jQuery) {
            callback(jQuery);
        }
        else {
            window.setTimeout(function() { checkReady(callback); }, 100);
        }
    };

    // Start polling...
    checkReady(function($) {
        // Use $ here...
    });
})();

var toReplaceNextButtonWithOurOwnLogic = function() {
    $('#next-submit-button').hide();
    var myButton = $('<input id="my-submit-button" type="button" name="mySubmitButton" class="button" value="Next" style="width: 60px;">');
    $('#next-submit-button').after(myButton);

    myButton.click(function(e){
        ourLogic();
    });
};

alert("hi");

////////////////////////////////////////////////////////////////////////////////
// Simple validation before proceeding
//
$(function(){
    //place script here
    $("#clickme").click(function() {
        $("#consent").toggle();
    });

    $('#next-submit-button').hide();
    var myButton = $('<input id="my-submit-button" type="button" name="mySubmitButton" class="button" value="Next" style="width: 60px;">');
    $('#next-submit-button').after(myButton);

    ////////////////////////////////////////////////////////////////////////////////
    // standard validation
    //
    var errorElem = $('<div class="varerror"><ul class="errorlist"><li>There is an error. Please read over the instructions and fix the error before continuing.</li></ul></div>');
    var Qid = "xx7bTVcQzw1";
    var inputId = "id_x7bTVcQzw1";
    var validVal = "I am stating that I will be alone for about 30 minutes while I do this experiment.".toLowerCase();

    var validateEntries = function() {
        return ($('#'+inputId).val().toLowerCase() == validVal);
    };

    myButton.click(function(e){
        if(validateEntries()){
            $('#'+Qid+'>div.question-body>div.varerror').remove();
            $('#'+Qid+'>div.question-body').data("showingError",false);
            $('#next-submit-button').click();
        } else {
            if($('#'+Qid+'>div.question-body').data("showingError")) {
                // already showing error
            } else {
                $('#'+Qid+'>div.question-body').prepend(errorElem.clone());
                $('#'+Qid+'>div.question-body').data("showingError",true);
            }
        }
        // either way, don't actually submit it.
        e.preventDefault();
    });
});


////////////////////////////////////////////////////////////////////////////////
// little bit more complex validation (partitioning work between two people)
//
$(function(){
    var selfQid = "xVeeAWgxUxS";
    var otherQid = "x7FgTnm2VsJ";
    var selfAlloc = "nQd4Zt2Kmq";
    var otherAlloc = "Hm2piqdR9m";
    var errorElem = $('<div class="varerror"><ul class="errorlist"><li>There is an error. Please read over the instructions and fix the error before continuing.</li></ul></div>');

    var validateEntries = function() {
        var salloc = parseInt($('#id_'+selfAlloc).val());
        var oalloc = parseInt($('#id_'+otherAlloc).val());
        if ((salloc + oalloc) === 500 ) {
            $('#'+selfQid+'>div.question-body').data("showingError",false);
            $('#'+otherQid+'>div.question-body').data("showingError",false);
            $('#'+selfQid+'>div.question-body>div.varerror').remove();
            $('#'+otherQid+'>div.question-body>div.varerror').remove();
            return true;
        } else {
            if($('#'+selfQid+'>div.question-body').data("showingError")) {
                // already showing error
            } else {
                $('#'+selfQid+'>div.question-body').prepend(errorElem.clone());
                $('#'+otherQid+'>div.question-body').prepend(errorElem.clone());
                $('#'+selfQid+'>div.question-body').data("showingError",true);
                $('#'+otherQid+'>div.question-body').data("showingError",true);
            }
        }
        return false;
    };

    $('#next-submit-button').hide();
    var myButton = $('<input id="my-submit-button" type="button" name="mySubmitButton" class="button" value="Submit" style="width: 60px;">');
    $('#next-submit-button').after(myButton);

    myButton.click(function(e){
        if(validateEntries()){
            $('#next-submit-button').click();
        }
        // either way, don't actually submit it.
        e.preventDefault();
    });
});

////////////////////////////////////////////////////////////////////////////////
// aFor the page after Intro, to find condition and send to the new page.
$(function(){
    var theNsid = "{{ nsid }}";

    var status_header = $("<div id='status_header' class='question-header'>Status of LAS transmission.<p><div id='status' class='question-body clearfix'></div><div>");

    var sendAjax = function() {
        $.ajax({
            type: "GET",
            url: "http://chdp.usask.ca:5000/exp-condition",
            data: {basic: "T0tGYzhxQ0piTXljNzg1VG04Zk9Wc1JsanpQQTlwOkl5T3ZTNzhFWVdpRmR3S0c0eHpHTjBpUXNaQ1hpNU9adzZ0SjdFMkU", nsid: theNsid.toLowerCase()},
            dataType: "jsonp",
            success: function(res) {location.replace(res.site + "?bnNpZA=" + res.bnNpZA);}
        });
    };

    var submitRedirect = function() {
        $("#survey-form").append(status_header);
        $("#status").text("Sending...").delay(2000).show(1, function(){ $(this).text("Receiving results...").delay(2000).show(1, function(){ $(this).text("Processing results, sending to next part of experiment...").delay(2000).show(1, function(){ sendAjax(); }); }); });
    };

    $('#next-submit-button').hide();
    var myButton = $('<input id="my-submit-button" type="button" name="mySubmitButton" class="button" value="Next" style="width: 60px;">');
    $('#next-submit-button').after(myButton);

    myButton.click(function(e){
        submitRedirect();
    });
});

////////////////////////////////////////////////////////////////////////////////
// found a partner
// For the page after Intro, to find condition and send to the new page.
$(function() {

    var msg = 'The system has found a partner for you. They have completed their portion of the experiment. After you have completed your portion, your pair will be finished the experiment.';
    var dialogObj = $('<div id="dialog-message" title="Found a Partner"> '
                      + '<p> <span class="ui-icon ui-icon-circle-check" style="float:left; margin:0 7px 50px 0;"></span> '
                      + msg
                      + '</p> </div>');

    $("#survey").append(dialogObj).hide();

    // for the notification that the system found them a partner
    var showMyDialog = function(obj) {
        obj.show();
        obj.dialog({
            modal: true,
            buttons: {
                Ok: function() {
                    $( this ).dialog( "close" );
                }
            }
        });
    };

    $('#next-submit-button').attr("disabled", "disabled");

    $("#survey").show().delay(20000).show(1, function(){
        $('#next-submit-button').removeAttr("disabled");
        showMyDialog(dialogObj);
    });
});



////////////////////////////////////////////////////////////////////////////////
// workspace / scratch
//
var workspace = function() {
    alert("hi");
    skewer.log("hi");

    $.get("http://chdp.usask.ca:5000/", myRes);

    function myRes(res) {
        skewer.log(res);
    }
    myRes("test");

    $('#'+selfQid+'>div.question-body').removeData("showingError");


    var out = findEvents($('#my-submit-button'));
    skewer.log(out);

    function findEvents(element) {

        var events = element.data('events');
        if (events !== undefined)
            return events;

        events = $.data(element, 'events');
        if (events !== undefined)
            return events;

        events = $._data(element, 'events');
        if (events !== undefined)
            return events;

        // events = $._data(element[0], 'events');
        // if (events !== undefined)
        //     return events;

        return undefined;
    }

    var logIt = function (obj) {
        $.each(obj, function(key, element) {
            skewer.log('kvs: ' + key + ' : ' + element + "; ");
        });
    };

    // remove junk -- testing
    //$('#'+selfQid+'>div.question-body').data("showingError",false);
    // $('#'+otherQid+'>div.question-body').data("showingError",false);
    // $('#'+selfQid+'>div.question-body>div.varerror').remove();
    // $('#'+otherQid+'>div.question-body>div.varerror').remove();
};
