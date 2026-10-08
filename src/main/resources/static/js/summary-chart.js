// "Spending by day" column chart on the summary page, drawn with Chart.js from a CDN.
// The data comes from data-* attributes on the canvas; the same numbers are in the table view below it.
(function () {
    var canvas = document.getElementById('daily-chart');
    if (!canvas) {
        return;
    }
    if (typeof window.Chart === 'undefined') {
        // CDN blocked or offline: keep the page useful with the table view
        canvas.closest('.chart-box').hidden = true;
        document.getElementById('chart-fallback').hidden = false;
        return;
    }

    var parts = canvas.getAttribute('data-month').split('-');
    var year = Number(parts[0]);
    var monthIndex = Number(parts[1]) - 1;
    var currency = canvas.getAttribute('data-currency');
    var amounts = canvas.getAttribute('data-amounts').split(',').map(Number);
    var labels = amounts.map(function (_, i) {
        return String(i + 1);
    });

    function cssVar(name) {
        return getComputedStyle(document.documentElement).getPropertyValue(name).trim();
    }

    function money(value, decimals) {
        return currency + value.toLocaleString('en-US', {
            minimumFractionDigits: decimals,
            maximumFractionDigits: decimals
        });
    }

    function colors() {
        return {
            bar: cssVar('--chart-bar'),
            barHover: cssVar('--chart-bar-hover'),
            grid: cssVar('--chart-grid'),
            text: cssVar('--muted')
        };
    }

    var c = colors();
    var chart = new window.Chart(canvas, {
        type: 'bar',
        data: {
            labels: labels,
            datasets: [{
                data: amounts,
                backgroundColor: c.bar,
                hoverBackgroundColor: c.barHover,
                maxBarThickness: 24,
                borderRadius: {topLeft: 4, topRight: 4},
                categoryPercentage: 0.85,
                barPercentage: 0.9
            }]
        },
        options: {
            maintainAspectRatio: false,
            animation: false,
            // Hovering anywhere in a day's column shows its tooltip, not just the painted bar
            interaction: {mode: 'index', intersect: false},
            plugins: {
                legend: {display: false},
                tooltip: {
                    displayColors: false,
                    titleFont: {weight: 'normal'},
                    bodyFont: {weight: 'bold', size: 14},
                    callbacks: {
                        title: function (items) {
                            var date = new Date(year, monthIndex, items[0].dataIndex + 1);
                            return date.toLocaleDateString(undefined, {weekday: 'short', day: 'numeric', month: 'short'});
                        },
                        label: function (item) {
                            return money(item.parsed.y, 2);
                        }
                    }
                }
            },
            scales: {
                x: {
                    grid: {display: false},
                    border: {color: c.grid},
                    ticks: {color: c.text, maxRotation: 0, autoSkipPadding: 8}
                },
                y: {
                    beginAtZero: true,
                    border: {display: false},
                    grid: {color: c.grid, lineWidth: 1},
                    ticks: {
                        color: c.text,
                        maxTicksLimit: 5,
                        callback: function (value) {
                            return money(Number(value), 0);
                        }
                    }
                }
            }
        }
    });

    // Follow the system light/dark setting without a reload
    window.matchMedia('(prefers-color-scheme: dark)').addEventListener('change', function () {
        var next = colors();
        var dataset = chart.data.datasets[0];
        dataset.backgroundColor = next.bar;
        dataset.hoverBackgroundColor = next.barHover;
        chart.options.scales.x.border.color = next.grid;
        chart.options.scales.x.ticks.color = next.text;
        chart.options.scales.y.grid.color = next.grid;
        chart.options.scales.y.ticks.color = next.text;
        chart.update();
    });
})();
