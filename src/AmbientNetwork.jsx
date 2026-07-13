import { useEffect, useRef } from "react";

const COLORS = [
  [202, 255, 56],
  [184, 255, 74],
  [168, 85, 247],
  [124, 58, 237],
  [221, 214, 254],
];

const randomBetween = (min, max) => min + Math.random() * (max - min);

export default function AmbientNetwork() {
  const canvasRef = useRef(null);
  const artworkRef = useRef(null);

  useEffect(() => {
    const canvas = canvasRef.current;
    const artwork = artworkRef.current;
    const context = canvas.getContext("2d", { alpha: true });
    const reducedMotion = window.matchMedia("(prefers-reduced-motion: reduce)");
    const coarsePointer = window.matchMedia("(pointer: coarse)").matches;
    const pointer = { x: -1000, y: -1000, active: false };
    let width = 0;
    let height = 0;
    let nodes = [];
    let frame = 0;
    let lastTime = performance.now();
    let lastScrollY = window.scrollY;
    let scrollImpulse = 0;
    let visible = !document.hidden;

    const createNodes = () => {
      const area = width * height;
      const count = Math.max(coarsePointer ? 26 : 42, Math.min(coarsePointer ? 42 : 82, Math.round(area / 23000)));
      nodes = Array.from({ length: count }, (_, index) => ({
        x: Math.random() * width,
        y: Math.random() * height,
        vx: randomBetween(-0.075, 0.075),
        vy: randomBetween(-0.06, 0.06),
        radius: index % 9 === 0 ? randomBetween(2.4, 3.6) : randomBetween(1.1, 2.3),
        color: COLORS[index % COLORS.length],
        phase: Math.random() * Math.PI * 2,
      }));
    };

    const resize = () => {
      const ratio = Math.min(window.devicePixelRatio || 1, 1.5);
      width = window.innerWidth;
      height = window.innerHeight;
      canvas.width = Math.round(width * ratio);
      canvas.height = Math.round(height * ratio);
      canvas.style.width = `${width}px`;
      canvas.style.height = `${height}px`;
      context.setTransform(ratio, 0, 0, ratio, 0, 0);
      createNodes();
    };

    const onPointerMove = (event) => {
      if (coarsePointer || reducedMotion.matches) return;
      pointer.x = event.clientX;
      pointer.y = event.clientY;
      pointer.active = true;
    };

    const onPointerLeave = () => {
      pointer.active = false;
    };

    const onScroll = () => {
      const nextY = window.scrollY;
      scrollImpulse += Math.max(-3.2, Math.min(3.2, (nextY - lastScrollY) * 0.018));
      lastScrollY = nextY;
    };

    const onVisibility = () => {
      visible = !document.hidden;
      if (visible && !reducedMotion.matches) {
        lastTime = performance.now();
        frame = requestAnimationFrame(draw);
      } else if (frame) {
        cancelAnimationFrame(frame);
      }
    };

    const drawConnections = (time) => {
      const maxDistance = width < 760 ? 105 : 138;
      for (let i = 0; i < nodes.length; i += 1) {
        for (let j = i + 1; j < nodes.length; j += 1) {
          const dx = nodes[i].x - nodes[j].x;
          const dy = nodes[i].y - nodes[j].y;
          const distance = Math.hypot(dx, dy);
          if (distance > maxDistance) continue;
          const alpha = (1 - distance / maxDistance) * 0.2;
          const limeConnection = (i + j) % 8 === 0;
          context.strokeStyle = limeConnection
            ? `rgba(202,255,56,${alpha * 0.7})`
            : `rgba(168,85,247,${alpha})`;
          context.lineWidth = limeConnection ? 0.8 : 0.65;
          context.beginPath();
          const sway = Math.sin(time * 0.0007 + nodes[i].phase) * 8;
          context.moveTo(nodes[i].x, nodes[i].y);
          context.quadraticCurveTo((nodes[i].x + nodes[j].x) / 2, (nodes[i].y + nodes[j].y) / 2 + sway, nodes[j].x, nodes[j].y);
          context.stroke();
        }
      }
    };

    function draw(time) {
      if (!visible) return;
      const delta = Math.min(32, time - lastTime) / 16.67;
      lastTime = time;
      context.clearRect(0, 0, width, height);
      scrollImpulse *= 0.92;

      const maxScroll = Math.max(1, document.documentElement.scrollHeight - height);
      const progress = Math.min(1, window.scrollY / maxScroll);
      const idleDrift = Math.sin(time * 0.00008) * Math.min(28, width * 0.025);
      const scrollDrift = progress * Math.min(width * 0.34, 520);
      artwork.style.setProperty("--art-drift", `${idleDrift - scrollDrift}px`);
      artwork.style.setProperty("--art-float", `${Math.cos(time * 0.00011) * 12}px`);

      nodes.forEach((node) => {
        node.vx += (-0.006 - scrollImpulse * 0.002) * delta;
        node.vx *= 0.992;
        node.vy *= 0.996;
        node.vx = Math.max(-0.7, Math.min(0.32, node.vx));

        if (pointer.active) {
          const dx = node.x - pointer.x;
          const dy = node.y - pointer.y;
          const distance = Math.max(1, Math.hypot(dx, dy));
          const radius = 145;
          if (distance < radius) {
            const force = (1 - distance / radius) * 0.55;
            node.vx += (dx / distance) * force;
            node.vy += (dy / distance) * force;
          }
        }

        node.x += node.vx * delta;
        node.y += node.vy * delta;
        if (node.x < -18) node.x = width + 18;
        if (node.x > width + 18) node.x = -18;
        if (node.y < -18) node.y = height + 18;
        if (node.y > height + 18) node.y = -18;
      });

      drawConnections(time);
      nodes.forEach((node) => {
        const pulse = 0.75 + Math.sin(time * 0.0015 + node.phase) * 0.22;
        const [r, g, b] = node.color;
        context.shadowBlur = node.radius > 2.5 ? 16 : 8;
        context.shadowColor = `rgba(${r},${g},${b},0.7)`;
        context.fillStyle = `rgba(${r},${g},${b},${pulse})`;
        context.beginPath();
        context.arc(node.x, node.y, node.radius, 0, Math.PI * 2);
        context.fill();
      });
      context.shadowBlur = 0;
      frame = requestAnimationFrame(draw);
    }

    const renderReducedMotion = () => {
      if (!reducedMotion.matches) {
        lastTime = performance.now();
        frame = requestAnimationFrame(draw);
        return;
      }
      if (frame) cancelAnimationFrame(frame);
      context.clearRect(0, 0, width, height);
      artwork.style.setProperty("--art-drift", "0px");
      artwork.style.setProperty("--art-float", "0px");
      drawConnections(0);
      nodes.forEach((node) => {
        const [r, g, b] = node.color;
        context.fillStyle = `rgba(${r},${g},${b},0.55)`;
        context.beginPath();
        context.arc(node.x, node.y, node.radius, 0, Math.PI * 2);
        context.fill();
      });
    };

    resize();
    window.addEventListener("resize", resize);
    window.addEventListener("pointermove", onPointerMove, { passive: true });
    window.addEventListener("pointerleave", onPointerLeave);
    window.addEventListener("scroll", onScroll, { passive: true });
    document.addEventListener("visibilitychange", onVisibility);
    reducedMotion.addEventListener("change", renderReducedMotion);
    renderReducedMotion();

    return () => {
      cancelAnimationFrame(frame);
      window.removeEventListener("resize", resize);
      window.removeEventListener("pointermove", onPointerMove);
      window.removeEventListener("pointerleave", onPointerLeave);
      window.removeEventListener("scroll", onScroll);
      document.removeEventListener("visibilitychange", onVisibility);
      reducedMotion.removeEventListener("change", renderReducedMotion);
    };
  }, []);

  return (
    <div className="ambient-stage" aria-hidden="true">
      <div className="ambient-artwork" ref={artworkRef} />
      <canvas className="ambient-canvas" ref={canvasRef} />
      <div className="ambient-vignette" />
    </div>
  );
}
