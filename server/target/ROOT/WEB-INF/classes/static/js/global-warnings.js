/**
 * Global Warnings Script
 * Fetches and displays system warnings across all pages.
 */

document.addEventListener("DOMContentLoaded", function () {
    const WARNINGS_API_URL = '/api/system-warnings';
    const POLL_INTERVAL_MS = 10000; // 10 seconds

    function fetchWarnings() {
        fetch(WARNINGS_API_URL)
            .then(response => response.json())
            .then(data => {
                if (data.status === 'ok' && data.warnings && data.warnings.length > 0) {
                    renderBanner(data.warnings);
                } else {
                    removeBanner();
                }
            })
            .catch(err => console.error('Error fetching system warnings:', err));
    }

    function renderBanner(warnings) {
        let banner = document.getElementById('global-dynamic-warning-banner');
        
        if (!banner) {
            // Create banner if it doesn't exist
            banner = document.createElement('div');
            banner.id = 'global-dynamic-warning-banner';
            banner.className = 'global-warning-banner';
            
            // Wait for body to be available
            if (document.body) {
                // Insert at the top of the body
                document.body.insertBefore(banner, document.body.firstChild);
            }
        }

        // Generate HTML for warnings
        let html = `
            <div class="global-warning-icon">⚠️</div>
            <div>
                <h3 style="margin:0; color: #ff6b6b; font-size:1.2rem;">SYSTEM WARNUNG</h3>
                <ul style="margin: 5px 0 0 0; padding-left: 20px;">
        `;
        
        warnings.forEach(w => {
            const accInfo = `${w.accountName} [${w.type}]`;
            w.messages.forEach(m => {
                html += `<li style="margin-bottom: 2px;"><b>${accInfo}:</b> ${m}</li>`;
            });
        });
        
        html += `</ul></div>`;
        banner.innerHTML = html;
        banner.style.display = 'flex';
    }

    function removeBanner() {
        const banner = document.getElementById('global-dynamic-warning-banner');
        if (banner) {
            banner.style.display = 'none';
        }
        
        // Also hide legacy banner if it exists
        const legacyBanner = document.getElementById('sync-warning-banner');
        if (legacyBanner) {
            legacyBanner.style.display = 'none';
        }
    }

    // Initial fetch
    fetchWarnings();

    // Poll periodically
    setInterval(fetchWarnings, POLL_INTERVAL_MS);
});
