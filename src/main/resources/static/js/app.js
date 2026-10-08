// Small progressive enhancements. Every page still works without JavaScript.

// Ask before submitting any form marked with data-confirm (deletes, removals).
document.addEventListener('submit', function (event) {
    var message = event.target.getAttribute('data-confirm');
    if (message && !window.confirm(message)) {
        event.preventDefault();
    }
});
