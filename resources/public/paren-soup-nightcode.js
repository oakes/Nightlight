var editor = null;
var lastTextContent = "";

var autosave = paren_soup.core.debounce_function(function() {
    //window.java.onautosave();
}, 1000);

function init() {
    markClean();
    var parent = document.getElementById('paren-soup');
    editor = paren_soup.core.init(parent, {
        "change-callback": function(e) {
            if (e.type == "keyup") {
                autosave();
            }
            //window.java.onchange();
        }
    });
    var content = document.getElementById('content');
    content.style.whiteSpace = "pre";
    content.focus();
}

function initConsole(isRepl) {
    var parent = document.getElementById('paren-soup');
    editor = paren_soup.core.init(parent, {
        "change-callback": function(e) {
            //window.java.onchange();
            if (e.type == "keyup") {
            	parent.scrollTop = parent.scrollHeight;
    		}
        },
        "console-callback": function(text) {
            //window.java.onenter(text + "\n");
        },
        "disable-clj?": !isRepl,
        "append-limit": 2000
    });
    var content = document.getElementById('content');
    content.style.whiteSpace = "pre-wrap";
}

function undo() {
    paren_soup.core.undo(editor);
    //window.java.onautosave();
}

function redo() {
    paren_soup.core.redo(editor);
    //window.java.onautosave();
}

function canUndo() {
    if (editor == null) {
        return false;
    }
    return paren_soup.core.can_undo(editor);
}

function canRedo() {
    if (editor == null) {
        return false;
    }
    return paren_soup.core.can_redo(editor);
}

function setTextContent(content) {
    document.getElementById('content').textContent = content;
}

function getTextContent() {
    return document.getElementById('content').textContent;
}

function getSelectedText() {
	var text = paren_soup.core.selected_text() || paren_soup.core.focused_text();
    if (text) {
    	text = parinfer.parenMode(text, {}).text;
    	text = parinfer.indentMode(text, {}).text;
    }
    return text;
}

function markClean() {
    lastTextContent = getTextContent();
    //window.java.onchange();
}

function isClean() {
    return lastTextContent == getTextContent();
}

function showInstaRepl() {
    var parent = document.getElementById('paren-soup');
    var numbers = document.getElementById('numbers');
    var instarepl = document.createElement('div');
    instarepl.className = 'instarepl';
    instarepl.id = 'instarepl';
    parent.insertBefore(instarepl, numbers);
    init();
}

function hideInstaRepl() {
    var parent = document.getElementById('paren-soup');
    var instarepl = document.getElementById('instarepl');
    parent.removeChild(instarepl);
    init();
}

function append(text) {
    paren_soup.core.append_text(editor, text);
    var parent = document.getElementById('paren-soup');
    parent.scrollTop = parent.scrollHeight;
}

function changeTheme(isDark) {
	var oldlink = document.getElementsByTagName("link").item(0);
	var newlink = document.createElement("link");
	newlink.setAttribute("rel", "stylesheet");
	newlink.setAttribute("type", "text/css");
	newlink.setAttribute("href", isDark ? "paren-soup-dark.css" : "paren-soup-light.css");
	document.getElementsByTagName("head").item(0).replaceChild(newlink, oldlink);
}

function setTextSize(size) {
	document.getElementById('paren-soup').style.fontSize = size + 'px';
}

window.onload = function() {
    //window.java.onload();
    //window.java.onchange();
    init();
};
