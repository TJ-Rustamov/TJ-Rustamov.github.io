import { useEffect, useRef } from "react";

const COLORS = {
  lime: [202, 255, 56],
  limeSoft: [184, 255, 74],
  violet: [168, 85, 247],
  violetDeep: [109, 40, 217],
  lavender: [221, 214, 254],
};

const NODE_LAYOUT = [
  { x: .53, y: .12, r: 8, color: "lime", float: 12 },
  { x: .61, y: .18, r: 5, color: "violet", float: 15 },
  { x: .70, y: .15, r: 4, color: "lavender", float: 9 },
  { x: .79, y: .23, r: 8, color: "violetDeep", float: 16 },
  { x: .90, y: .17, r: 5, color: "limeSoft", float: 10 },
  { x: 1.01, y: .26, r: 10, color: "violet", float: 14 },
  { x: .48, y: .34, r: 10, color: "violetDeep", float: 18 },
  { x: .61, y: .31, r: 7, color: "lime", float: 13 },
  { x: .73, y: .38, r: 5, color: "violet", float: 10 },
  { x: .84, y: .34, r: 10, color: "lavender", float: 17 },
  { x: .96, y: .42, r: 7, color: "lime", float: 14 },
  { x: .51, y: .53, r: 6, color: "limeSoft", float: 11 },
  { x: .63, y: .49, r: 8, color: "violet", float: 15 },
  { x: .76, y: .56, r: 12, color: "lime", float: 19 },
  { x: .89, y: .51, r: 7, color: "violetDeep", float: 12 },
  { x: 1.03, y: .59, r: 11, color: "lavender", float: 17 },
  { x: .46, y: .72, r: 8, color: "violet", float: 16 },
  { x: .58, y: .66, r: 6, color: "lime", float: 11 },
  { x: .70, y: .75, r: 10, color: "violetDeep", float: 18 },
  { x: .83, y: .68, r: 7, color: "limeSoft", float: 12 },
  { x: .95, y: .78, r: 9, color: "violet", float: 16 },
  { x: 1.07, y: .71, r: 6, color: "lime", float: 10 },
  { x: .54, y: .88, r: 5, color: "lime", float: 13 },
  { x: .68, y: .91, r: 8, color: "lavender", float: 18 },
  { x: .82, y: .87, r: 12, color: "lime", float: 15 },
  { x: .98, y: .92, r: 7, color: "violetDeep", float: 12 },
];

const LINKS = [
  [0, 1, -.08], [1, 2, .04], [2, 3, -.09], [3, 4, .08], [4, 5, -.04],
  [6, 7, -.12], [7, 8, .07], [8, 9, -.06], [9, 10, .11],
  [0, 7, .12], [2, 8, -.08], [3, 9, .06], [5, 10, -.12],
  [6, 11, .08], [7, 12, -.06], [8, 13, .09], [9, 14, -.08], [10, 15, .08],
  [11, 12, -.09], [12, 13, .12], [13, 14, -.07], [14, 15, .1],
  [11, 17, .1], [13, 18, -.08], [14, 19, .08], [15, 21, -.1],
  [16, 17, -.06], [17, 18, .1], [18, 19, -.09], [19, 20, .08], [20, 21, -.06],
  [16, 22, .1], [18, 23, -.08], [19, 24, .07], [21, 25, -.1],
  [22, 23, -.06], [23, 24, .09], [24, 25, -.05],
];

const PARTICLE_COLORS = [COLORS.lime, COLORS.violet, COLORS.violetDeep, COLORS.lavender];
const randomBetween = (min, max) => min + Math.random() * (max - min);

const rgba = ([r, g, b], alpha) => `rgba(${r},${g},${b},${alpha})`;

const quadraticPoint = (start, control, end, t) => {
  const inverse = 1 - t;
  return {
    x: inverse * inverse * start.x + 2 * inverse * t * control.x + t * t * end.x,
    y: inverse * inverse * start.y + 2 * inverse * t * control.y + t * t * end.y,
  };
};

export default function AmbientNetwork() {
  const canvasRef = useRef(null);

  useEffect(() => {
    const canvas = canvasRef.current;
    const context = canvas.getContext("2d", { alpha: true });
    const reducedMotion = window.matchMedia("(prefers-reduced-motion: reduce)");
    const coarsePointer = window.matchMedia("(pointer: coarse)").matches;
    const pointer = { x: -1000, y: -1000, active: false };
    let width = 0;
    let height = 0;
    let particles = [];
    let structuredNodes = [];
    let frame = 0;
    let lastTime = performance.now();
    let lastScrollY = window.scrollY;
    let scrollImpulse = 0;
    let visible = !document.hidden;

    const createScene = () => {
      const area = width * height;
      const count = Math.max(coarsePointer ? 18 : 30, Math.min(coarsePointer ? 30 : 54, Math.round(area / 32000)));
      particles = Array.from({ length: count }, (_, index) => ({
        x: Math.random() * width,
        y: Math.random() * height,
        vx: randomBetween(-.08, .08),
        vy: randomBetween(-.06, .06),
        radius: index % 8 === 0 ? randomBetween(2.2, 3.4) : randomBetween(.8, 1.9),
        color: PARTICLE_COLORS[index % PARTICLE_COLORS.length],
        phase: Math.random() * Math.PI * 2,
      }));
      structuredNodes = NODE_LAYOUT.map((node, index) => ({
        ...node,
        phase: index * .71 + Math.random() * .4,
        dx: 0,
        dy: 0,
        vx: 0,
        vy: 0,
        currentX: 0,
        currentY: 0,
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
      createScene();
    };

    const onPointerMove = (event) => {
      if (coarsePointer || reducedMotion.matches) return;
      pointer.x = event.clientX;
      pointer.y = event.clientY;
      pointer.active = true;
    };
    const onPointerLeave = () => { pointer.active = false; };
    const onScroll = () => {
      const nextY = window.scrollY;
      scrollImpulse += Math.max(-3.5, Math.min(3.5, (nextY - lastScrollY) * .02));
      lastScrollY = nextY;
    };

    const sceneShiftFor = (time) => {
      const maxScroll = Math.max(1, document.documentElement.scrollHeight - height);
      const progress = Math.min(1, window.scrollY / maxScroll);
      return Math.sin(time * .00009) * Math.min(22, width * .02) - progress * Math.min(width * .52, 720);
    };

    const drawLargeForms = (time, shift) => {
      const size = Math.min(width, height);
      const centerX = width * .79 + shift + Math.sin(time * .00018) * 12;
      const centerY = height * .48 + Math.cos(time * .00016) * 9;
      const radius = size * .24;
      const halo = context.createRadialGradient(centerX - radius * .18, centerY - radius * .18, radius * .08, centerX, centerY, radius);
      halo.addColorStop(0, "rgba(124,58,237,.5)");
      halo.addColorStop(.5, "rgba(76,29,149,.28)");
      halo.addColorStop(1, "rgba(7,5,17,0)");
      context.fillStyle = halo;
      context.beginPath();
      context.arc(centerX, centerY, radius, 0, Math.PI * 2);
      context.fill();

      for (let ring = 0; ring < 3; ring += 1) {
        context.strokeStyle = ring === 1 ? "rgba(202,255,56,.15)" : "rgba(168,85,247,.22)";
        context.lineWidth = .8;
        context.beginPath();
        context.ellipse(centerX, centerY, radius * (1 + ring * .22), radius * (.78 + ring * .15), time * .00004 + ring * .42, 0, Math.PI * 2);
        context.stroke();
      }

      const bottomGradient = context.createLinearGradient(0, height * .7, 0, height);
      bottomGradient.addColorStop(0, "rgba(76,29,149,0)");
      bottomGradient.addColorStop(.45, "rgba(76,29,149,.2)");
      bottomGradient.addColorStop(1, "rgba(124,58,237,.42)");
      context.fillStyle = bottomGradient;
      context.beginPath();
      context.moveTo(width * .38 + shift, height);
      context.bezierCurveTo(width * .53 + shift, height * .67, width * .67 + shift, height * .94, width * .82 + shift, height * .73);
      context.bezierCurveTo(width * .94 + shift, height * .58, width * 1.08 + shift, height * .75, width * 1.2 + shift, height * .61);
      context.lineTo(width * 1.3 + shift, height);
      context.closePath();
      context.fill();

      for (let line = 0; line < 6; line += 1) {
        context.strokeStyle = `rgba(168,85,247,${.07 + line * .012})`;
        context.lineWidth = .7;
        context.beginPath();
        context.moveTo(width * .35 + shift, height * (.92 + line * .015));
        context.bezierCurveTo(width * .58 + shift, height * (.66 + line * .018), width * .78 + shift, height * (.96 + line * .01), width * 1.15 + shift, height * (.67 + line * .014));
        context.stroke();
      }
    };

    const updateStructuredNodes = (time, delta, shift, interactive) => {
      structuredNodes.forEach((node) => {
        const baseX = node.x * width + shift + Math.sin(time * .00045 + node.phase) * node.float;
        const baseY = node.y * height + Math.cos(time * .00038 + node.phase) * node.float * .62;
        if (interactive && pointer.active) {
          const dx = baseX + node.dx - pointer.x;
          const dy = baseY + node.dy - pointer.y;
          const distance = Math.max(1, Math.hypot(dx, dy));
          if (distance < 175) {
            const force = (1 - distance / 175) * 1.1;
            node.vx += (dx / distance) * force;
            node.vy += (dy / distance) * force;
          }
        }
        node.vx += -node.dx * .012 * delta;
        node.vy += -node.dy * .012 * delta;
        node.vx *= .91;
        node.vy *= .91;
        node.dx += node.vx * delta;
        node.dy += node.vy * delta;
        node.currentX = baseX + node.dx;
        node.currentY = baseY + node.dy;
      });
    };

    const drawStructuredLinks = (time) => {
      LINKS.forEach(([fromIndex, toIndex, curve], index) => {
        const from = structuredNodes[fromIndex];
        const to = structuredNodes[toIndex];
        const control = {
          x: (from.currentX + to.currentX) / 2,
          y: (from.currentY + to.currentY) / 2 + curve * height,
        };
        const isLime = index % 7 === 0;
        const dotted = index % 6 === 2;
        context.strokeStyle = isLime ? "rgba(202,255,56,.3)" : "rgba(139,92,246,.42)";
        context.lineWidth = isLime ? 1.2 : 1;
        context.setLineDash(dotted ? [2, 9] : []);
        context.lineDashOffset = dotted ? -time * .018 : 0;
        context.beginPath();
        context.moveTo(from.currentX, from.currentY);
        context.quadraticCurveTo(control.x, control.y, to.currentX, to.currentY);
        context.stroke();
        context.setLineDash([]);

        if (index % 5 === 0) {
          const t = (time * .00012 + index * .17) % 1;
          const pulse = quadraticPoint(from, control, to, t);
          context.shadowBlur = 13;
          context.shadowColor = isLime ? "#caff38" : "#a855f7";
          context.fillStyle = isLime ? "rgba(202,255,56,.95)" : "rgba(216,180,254,.9)";
          context.beginPath();
          context.arc(pulse.x, pulse.y, 2.4, 0, Math.PI * 2);
          context.fill();
          context.shadowBlur = 0;
        }
      });
    };

    const drawStructuredNodes = (time) => {
      structuredNodes.forEach((node) => {
        const color = COLORS[node.color];
        const pulse = 1 + Math.sin(time * .0014 + node.phase) * .16;
        const radius = node.r * pulse;
        context.shadowBlur = node.color.startsWith("lime") ? 20 : 14;
        context.shadowColor = rgba(color, .75);
        context.fillStyle = rgba(color, node.color === "lavender" ? .8 : .74);
        context.beginPath();
        context.arc(node.currentX, node.currentY, radius, 0, Math.PI * 2);
        context.fill();
        context.shadowBlur = 0;
        if (node.r >= 9) {
          context.strokeStyle = rgba(color, .24);
          context.lineWidth = 1;
          context.beginPath();
          context.arc(node.currentX, node.currentY, radius * 2.25, 0, Math.PI * 2);
          context.stroke();
        }
      });
    };

    const drawWaveform = (time, shift) => {
      const startX = width * .70 + shift;
      const endX = width * 1.06 + shift;
      const centerY = height * .13 + Math.sin(time * .00045) * 5;
      const bars = width < 760 ? 34 : 52;
      const spacing = (endX - startX) / bars;
      context.save();
      context.shadowBlur = 15;
      context.shadowColor = "rgba(202,255,56,.65)";
      context.strokeStyle = "rgba(202,255,56,.72)";
      context.lineWidth = width < 760 ? 2 : 2.6;
      for (let index = 0; index < bars; index += 1) {
        const normalized = index / Math.max(1, bars - 1);
        const envelope = Math.sin(normalized * Math.PI);
        const speech = Math.abs(Math.sin(time * .0042 + index * .68) * .58 + Math.sin(time * .0021 - index * .31) * .42);
        const heightValue = 5 + envelope * (18 + speech * Math.min(72, height * .085));
        const x = startX + index * spacing;
        context.beginPath();
        context.moveTo(x, centerY - heightValue / 2);
        context.lineTo(x, centerY + heightValue / 2);
        context.stroke();
      }
      context.restore();
      context.strokeStyle = "rgba(168,85,247,.32)";
      context.lineWidth = 1;
      context.beginPath();
      context.moveTo(startX - 55, centerY);
      context.lineTo(endX + 35, centerY);
      context.stroke();
    };

    const updateParticles = (time, delta, interactive) => {
      particles.forEach((particle) => {
        particle.vx += (-.004 - scrollImpulse * .0015) * delta;
        particle.vx *= .993;
        particle.vy *= .997;
        if (interactive && pointer.active) {
          const dx = particle.x - pointer.x;
          const dy = particle.y - pointer.y;
          const distance = Math.max(1, Math.hypot(dx, dy));
          if (distance < 145) {
            const force = (1 - distance / 145) * .6;
            particle.vx += (dx / distance) * force;
            particle.vy += (dy / distance) * force;
          }
        }
        particle.x += particle.vx * delta;
        particle.y += particle.vy * delta;
        if (particle.x < -15) particle.x = width + 15;
        if (particle.x > width + 15) particle.x = -15;
        if (particle.y < -15) particle.y = height + 15;
        if (particle.y > height + 15) particle.y = -15;
        const alpha = .36 + Math.sin(time * .0015 + particle.phase) * .18;
        context.fillStyle = rgba(particle.color, alpha);
        context.beginPath();
        context.arc(particle.x, particle.y, particle.radius, 0, Math.PI * 2);
        context.fill();
      });
    };

    const renderScene = (time, delta, interactive = true) => {
      context.clearRect(0, 0, width, height);
      scrollImpulse *= .92;
      const shift = sceneShiftFor(time);
      drawLargeForms(time, shift);
      updateStructuredNodes(time, delta, shift, interactive);
      drawStructuredLinks(time);
      drawWaveform(time, shift);
      drawStructuredNodes(time);
      updateParticles(time, delta, interactive);
    };

    function draw(time) {
      if (!visible || reducedMotion.matches) return;
      const delta = Math.min(32, time - lastTime) / 16.67;
      lastTime = time;
      renderScene(time, delta, true);
      frame = requestAnimationFrame(draw);
    }

    const renderMotionPreference = () => {
      cancelAnimationFrame(frame);
      if (reducedMotion.matches) {
        renderScene(0, 0, false);
      } else if (visible) {
        lastTime = performance.now();
        frame = requestAnimationFrame(draw);
      }
    };

    const onVisibility = () => {
      visible = !document.hidden;
      cancelAnimationFrame(frame);
      if (visible) renderMotionPreference();
    };

    resize();
    window.addEventListener("resize", resize);
    window.addEventListener("pointermove", onPointerMove, { passive: true });
    window.addEventListener("pointerleave", onPointerLeave);
    window.addEventListener("scroll", onScroll, { passive: true });
    document.addEventListener("visibilitychange", onVisibility);
    reducedMotion.addEventListener("change", renderMotionPreference);
    renderMotionPreference();

    return () => {
      cancelAnimationFrame(frame);
      window.removeEventListener("resize", resize);
      window.removeEventListener("pointermove", onPointerMove);
      window.removeEventListener("pointerleave", onPointerLeave);
      window.removeEventListener("scroll", onScroll);
      document.removeEventListener("visibilitychange", onVisibility);
      reducedMotion.removeEventListener("change", renderMotionPreference);
    };
  }, []);

  return (
    <div className="ambient-stage" aria-hidden="true">
      <canvas className="ambient-canvas" ref={canvasRef} />
      <div className="ambient-vignette" />
    </div>
  );
}
