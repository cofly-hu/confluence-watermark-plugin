(function() {
    var WATERMARK_OPACITY = 0.15;
    var WATERMARK_FONT_SIZE = 16;
    var WATERMARK_SPACING = 300;
    var WATERMARK_COLOR = '128,128,128';
    var REBUILD_INTERVAL = 2000;

    function escapeHtml(str) {
        var div = document.createElement('div');
        div.appendChild(document.createTextNode(str));
        return div.innerHTML;
    }

    function getWatermarkText() {
        var username = 'unknown';
        try {
            var meta = document.querySelector('meta[name="ajs-remote-user"]');
            if (meta && meta.content) username = meta.content;
        } catch(e) {}
        if (username === 'unknown') {
            try {
                var link = document.getElementById('user-menu-link');
                if (link && link.getAttribute('data-username'))
                    username = link.getAttribute('data-username');
            } catch(e) {}
        }
        if (username === 'unknown') username = 'anonymous';

        var now = new Date();
        var ts = now.getFullYear() + '-' +
            ('0' + (now.getMonth() + 1)).slice(-2) + '-' +
            ('0' + now.getDate()).slice(-2) + ' ' +
            ('0' + now.getHours()).slice(-2) + ':' +
            ('0' + now.getMinutes()).slice(-2) + ':' +
            ('0' + now.getSeconds()).slice(-2);
        return escapeHtml(username + ' | ' + ts);
    }

    function createWatermarkLayer(text) {
        var layer = document.createElement('div');
        layer.setAttribute('data-wm', '1');
        layer.style.cssText =
            'position:fixed!important;' +
            'top:0!important;left:0!important;' +
            'width:100vw!important;height:100vh!important;' +
            'pointer-events:none!important;' +
            'z-index:2147483647!important;' +
            'overflow:hidden!important;' +
            'display:block!important;' +
            'visibility:visible!important;' +
            'opacity:1!important;';

        var html = '';
        var cols = Math.ceil(window.innerWidth / WATERMARK_SPACING) + 2;
        var rows = Math.ceil(window.innerHeight / WATERMARK_SPACING) + 2;
        for (var i = -1; i < rows; i++) {
            for (var j = -1; j < cols; j++) {
                html += '<span data-wm="1" style="' +
                    'position:absolute!important;' +
                    'display:block!important;' +
                    'top:' + (i * WATERMARK_SPACING) + 'px!important;' +
                    'left:' + (j * WATERMARK_SPACING) + 'px!important;' +
                    'transform:rotate(-45deg)!important;' +
                    'font-size:' + WATERMARK_FONT_SIZE + 'px!important;' +
                    'color:rgba(' + WATERMARK_COLOR + ',' + WATERMARK_OPACITY + ')!important;' +
                    'white-space:nowrap!important;' +
                    'font-family:Arial,sans-serif!important;' +
                    'letter-spacing:1px!important;' +
                    'user-select:none!important;' +
                    'pointer-events:none!important;' +
                    'line-height:1.2!important;' +
                    '">' + text + '</span>';
            }
        }
        layer.innerHTML = html;
        return layer;
    }

    function injectWatermark() {
        var text = getWatermarkText();
        removeOldWatermarks();

        var layer1 = createWatermarkLayer(text);
        layer1.id = '_wm_a1';
        document.documentElement.appendChild(layer1);

        var layer2 = createWatermarkLayer(text);
        layer2.id = '_wm_b2';
        document.body.appendChild(layer2);
    }

    function removeOldWatermarks() {
        var old = document.querySelectorAll('[data-wm], #_wm_a1, #_wm_b2, #_wm_s3');
        for (var i = 0; i < old.length; i++) {
            try { old[i].parentNode.removeChild(old[i]); } catch(e) {}
        }
    }

    function disableWordExport() {
        var selectors = [
            'a[href*="exportWord"]',
            'a[href*="exportword"]',
            'a[data-content-type="word"]',
            '#export-to-word-link',
            '[id*="export-word"]'
        ];
        var links = document.querySelectorAll(selectors.join(','));
        for (var k = 0; k < links.length; k++) {
            links[k].style.setProperty('display', 'none', 'important');
            links[k].setAttribute('disabled', 'true');
        }
    }

    function observeAndProtect() {
        var observer = new MutationObserver(function(mutations) {
            var needRebuild = false;
            for (var i = 0; i < mutations.length; i++) {
                var m = mutations[i];
                if (m.type === 'childList') {
                    var removed = m.removedNodes;
                    for (var j = 0; j < removed.length; j++) {
                        var node = removed[j];
                        if (node.nodeType === 1 && node.getAttribute &&
                            node.getAttribute('data-wm') === '1') {
                            needRebuild = true;
                            break;
                        }
                    }
                }
                if (m.type === 'attributes' && m.target.getAttribute &&
                    m.target.getAttribute('data-wm') === '1') {
                    needRebuild = true;
                }
                if (needRebuild) break;
            }
            if (needRebuild) {
                observer.disconnect();
                injectWatermark();
                observeAndProtect();
            }
        });

        observer.observe(document.documentElement, {
            childList: true,
            subtree: true,
            attributes: true,
            attributeFilter: ['style', 'class', 'data-wm']
        });
    }

    function init() {
        injectWatermark();
        disableWordExport();
        observeAndProtect();

        setInterval(function() {
            if (!document.querySelector('[data-wm]')) {
                injectWatermark();
            }
            disableWordExport();
        }, REBUILD_INTERVAL);
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', init);
    } else {
        init();
    }
})();
