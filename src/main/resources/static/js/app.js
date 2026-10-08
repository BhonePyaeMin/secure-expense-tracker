// Small progressive enhancements. Every page still works without JavaScript.

// Ask before submitting any form marked with data-confirm (deletes, removals).
document.addEventListener('submit', function (event) {
    var message = event.target.getAttribute('data-confirm');
    if (message && !window.confirm(message)) {
        event.preventDefault();
    }
});

// Suggest a category from the title while typing (keyword rules on the server).
// Stops as soon as the user picks a category themselves, and never touches an existing choice.
(function () {
    var form = document.querySelector('form[data-suggest-url]');
    if (!form) {
        return;
    }
    var title = form.querySelector('#title');
    var category = form.querySelector('#category');
    var hint = document.getElementById('category-hint');
    var userChose = category.value !== '';
    var timer;

    category.addEventListener('change', function () {
        userChose = true;
        hint.textContent = '';
    });

    title.addEventListener('input', function () {
        if (userChose) {
            return;
        }
        clearTimeout(timer);
        timer = setTimeout(function () {
            var url = form.getAttribute('data-suggest-url') + '?title=' + encodeURIComponent(title.value);
            fetch(url, {headers: {'Accept': 'application/json'}})
                .then(function (response) {
                    return response.status === 200 ? response.json() : null;
                })
                .then(function (suggestion) {
                    if (userChose) {
                        return;
                    }
                    category.value = suggestion ? suggestion.category : '';
                    hint.textContent = !suggestion ? ''
                        : suggestion.source === 'history' ? 'Same category as last time'
                        : 'Category suggested from the title';
                })
                .catch(function () {
                    // Suggestions are a nice-to-have; ignore network errors
                });
        }, 250);
    });
})();
