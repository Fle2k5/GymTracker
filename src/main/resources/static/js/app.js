(() => {
    const validTabs = ['overview', 'exercises', 'schedules', 'history', 'nutrition', 'milestones'];
    const initial = validTabs.includes(document.body.dataset.activeTab) ? document.body.dataset.activeTab : 'overview';

    function showTab(name, updateUrl = true) {
        document.querySelectorAll('.tab-panel').forEach(panel => {
            const active = panel.id === `tab-${name}`;
            panel.classList.toggle('active', active);
            panel.setAttribute('aria-hidden', String(!active));
        });
        document.querySelectorAll('.tab-button').forEach(button => {
            const active = button.dataset.tab === name;
            button.classList.toggle('active', active);
            button.setAttribute('aria-selected', active);
        });
        if (updateUrl) history.replaceState({}, '', `/?tab=${name}`);
    }

    document.querySelectorAll('.tab-button').forEach(button => button.addEventListener('click', () => showTab(button.dataset.tab)));
    showTab(initial, false);

    const alert = document.querySelector('.alert');
    if (alert) setTimeout(() => alert.classList.add('fade'), 4500);

    document.querySelectorAll('form[data-confirm]').forEach(form => {
        form.addEventListener('submit', event => {
            if (!window.confirm(form.dataset.confirm)) event.preventDefault();
        });
    });

    document.querySelectorAll('form').forEach(form => {
        form.addEventListener('submit', event => {
            if (event.defaultPrevented) return;
            const button = form.querySelector('button[type="submit"]');
            if (button && form.checkValidity()) {
                button.disabled = true;
                button.setAttribute('aria-busy', 'true');
            }
        });
    });

    const celebration = document.getElementById('record-celebration');
    if (celebration && !window.matchMedia('(prefers-reduced-motion: reduce)').matches) {
        const canvas = document.getElementById('confetti-canvas');
        const context = canvas.getContext('2d');
        const colors = ['#b7ff4a', '#52e5ff', '#ff5fd2', '#ffb84d', '#8f7cff'];
        const resize = () => { canvas.width = window.innerWidth; canvas.height = window.innerHeight; };
        resize();
        window.addEventListener('resize', resize, { passive: true });
        const pieces = Array.from({ length: 150 }, () => ({
            x: Math.random() * canvas.width, y: -20 - Math.random() * canvas.height * .5,
            size: 5 + Math.random() * 8, speed: 2 + Math.random() * 5,
            drift: -2 + Math.random() * 4, rotation: Math.random() * Math.PI,
            color: colors[Math.floor(Math.random() * colors.length)]
        }));
        const start = performance.now();
        const draw = now => {
            context.clearRect(0, 0, canvas.width, canvas.height);
            pieces.forEach(piece => {
                piece.y += piece.speed; piece.x += piece.drift; piece.rotation += .06;
                context.save(); context.translate(piece.x, piece.y); context.rotate(piece.rotation);
                context.fillStyle = piece.color; context.fillRect(-piece.size / 2, -piece.size / 3, piece.size, piece.size * .65);
                context.restore();
            });
            if (now - start < 4300) requestAnimationFrame(draw);
            else celebration.classList.add('celebration-finished');
        };
        requestAnimationFrame(draw);
    }

    const imageInput = document.querySelector('input[type="file"][name="image"]');
    if (imageInput) imageInput.addEventListener('change', () => {
        const label = imageInput.closest('label');
        if (label && imageInput.files[0]) label.dataset.filename = imageInput.files[0].name;
    });
})();
