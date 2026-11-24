// 民宿在线预订平台 - 公共JS

// 通用API请求封装
window.api = {
    get: function(url) {
        return fetch(url).then(r => r.json());
    },
    post: function(url, data) {
        return fetch(url, {
            method: 'POST',
            headers: {'Content-Type': 'application/json'},
            body: JSON.stringify(data)
        }).then(r => r.json());
    }
};

// Toast提示
window.showToast = function(msg, type) {
    type = type || 'success';
    const bg = type === 'success' ? 'bg-success' : 'bg-danger';
    const div = document.createElement('div');
    div.className = 'position-fixed bottom-0 end-0 p-3';
    div.style.zIndex = '9999';
    div.innerHTML = `<div class="toast show ${bg} text-white" role="alert">
        <div class="toast-body">${msg}</div>
    </div>`;
    document.body.appendChild(div);
    setTimeout(() => div.remove(), 3000);
};
