package net.renan.photonplugin.menus.resource.preview.common.render;

import net.renan.photonplugin.menus.resource.preview.common.math.Mat4;
import net.renan.photonplugin.menus.resource.preview.common.math.Quat;
import net.renan.photonplugin.menus.resource.preview.common.math.Vec3;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class SceneLighting {
    public static final int MAX_LIGHTS = 96;
    private static final float VOLUME_SCALE = 0.03f;
    private static final float FOG_SCALE = 0.1f;
    private static final float FORWARD_SCATTERING = 0.3f;
    private static final int VOLUME_PIECES = 4;
    private static final int FOG_STEPS = 16;
    private static final float GROUND_ALBEDO = 0.7f;

    private final List<Light> lights;
    private final List<Light> volumetric = new ArrayList<>();
    private final List<Fog> fogs;
    private final Composite multiply = new FastComposite(FastComposite.Mode.MULTIPLY);
    private final Composite softAdd = new FastComposite(FastComposite.Mode.SOFT_ADD);
    private final Composite plainAdd = new FastComposite(FastComposite.Mode.ADD);

    public SceneLighting(List<Light> lights, List<Fog> fogs) {
        List<Light> sorted = new ArrayList<>(lights);
        sorted.sort(Comparator.comparingDouble((Light l) -> -(Math.max(l.r, Math.max(l.g, l.b)) * l.range)));
        this.lights = sorted.size() > MAX_LIGHTS ? new ArrayList<>(sorted.subList(0, MAX_LIGHTS)) : sorted;
        for (Light l : this.lights) {
            if (l.volumetric > 0) {
                volumetric.add(l);
            }
        }
        this.fogs = new ArrayList<>(fogs);
    }


    static float attenuation(float distanceSq, float range) {
        float range2 = range * range;
        if (distanceSq >= range2) {
            return 0f;
        }
        float ratio = (distanceSq * distanceSq) / (range2 * range2);
        float window = Math.max(0f, Math.min(1f, 1f - ratio));
        return window * window / (distanceSq + 1f);
    }

    static float smoothstep(float e0, float e1, float x) {
        if (e1 == e0) {
            return x < e0 ? 0f : 1f;
        }
        float t = Math.max(0f, Math.min(1f, (x - e0) / (e1 - e0)));
        return t * t * (3f - 2f * t);
    }

    static float phase(float cosTheta, float g) {
        float k = 1f + g * g - 2f * g * cosTheta;
        return (1f - g * g) / (k * (float) Math.sqrt(k));
    }

    private static float frac(float v) {
        return v - (float) Math.floor(v);
    }

    private static float hash(float x, float y, float z) {
        float px = frac(x * 0.3183099f + 0.1f) * 17f;
        float py = frac(y * 0.3183099f + 0.1f) * 17f;
        float pz = frac(z * 0.3183099f + 0.1f) * 17f;
        return frac(px * py * pz * (px + py + pz));
    }

    private static float mix(float a, float b, float t) {
        return a + (b - a) * t;
    }

    private static float valueNoise(float x, float y, float z) {
        float ix = (float) Math.floor(x);
        float iy = (float) Math.floor(y);
        float iz = (float) Math.floor(z);
        float fx = x - ix;
        float fy = y - iy;
        float fz = z - iz;
        fx = fx * fx * (3f - 2f * fx);
        fy = fy * fy * (3f - 2f * fy);
        fz = fz * fz * (3f - 2f * fz);
        return mix(
                mix(mix(hash(ix, iy, iz), hash(ix + 1, iy, iz), fx), mix(hash(ix, iy + 1, iz), hash(ix + 1, iy + 1, iz), fx), fy),
                mix(mix(hash(ix, iy, iz + 1), hash(ix + 1, iy, iz + 1), fx),
                        mix(hash(ix, iy + 1, iz + 1), hash(ix + 1, iy + 1, iz + 1), fx), fy),
                fz);
    }

    private static int to255(float v) {
        int i = Math.round(v * 255f);
        return i < 0 ? 0 : Math.min(i, 255);
    }

    private static float distanceSq(Vec3 a, Vec3 b) {
        float x = a.x - b.x;
        float y = a.y - b.y;
        float z = a.z - b.z;
        return x * x + y * y + z * z;
    }

    private static float[] coneRange(Vec3 o, float dx, float dy, float dz, Vec3 p, Vec3 axis, float cosOuter) {
        float wx = o.x - p.x;
        float wy = o.y - p.y;
        float wz = o.z - p.z;
        float dA = dx * axis.x + dy * axis.y + dz * axis.z;
        float wA = wx * axis.x + wy * axis.y + wz * axis.z;
        float c2 = cosOuter * cosOuter;
        float dw = dx * wx + dy * wy + dz * wz;
        float ww = wx * wx + wy * wy + wz * wz;
        float a2 = dA * dA - c2;
        float a1 = dA * wA - c2 * dw;
        float a0 = wA * wA - c2 * ww;
        if (Math.abs(a2) < 1e-6f) {
            a2 = a2 >= 0f ? 1e-6f : -1e-6f;
        }
        float disc = a1 * a1 - a2 * a0;
        float lo = -1e9f;
        float hi = 1e9f;
        if (disc < 0f) {
            if (a2 < 0f) {
                return new float[]{1f, 0f};
            }
        } else {
            float sq = (float) Math.sqrt(disc);
            float q = -(a1 + (a1 >= 0f ? sq : -sq));
            float r0 = q / a2;
            float r1 = Math.abs(q) > 1e-12f ? a0 / q : r0;
            if (a2 < 0f) {
                lo = Math.min(r0, r1);
                hi = Math.max(r0, r1);
            } else if (dA > 0f) {
                lo = Math.max(r0, r1);
            } else {
                hi = Math.min(r0, r1);
            }
        }
        if (Math.abs(dA) < 1e-6f) {
            if (wA < 0f) {
                return new float[]{1f, 0f};
            }
        } else if (dA > 0f) {
            lo = Math.max(lo, -wA / dA);
        } else {
            hi = Math.min(hi, -wA / dA);
        }
        return new float[]{lo, hi};
    }

    public boolean isEmpty() {
        return lights.isEmpty() && fogs.isEmpty();
    }

    public boolean hasLights() {
        return !lights.isEmpty();
    }

    public List<Light> lights() {
        return lights;
    }

    public void litParticleBoost(Vec3 position, float intensity, float[] out) {
        out[0] = out[1] = out[2] = 0f;
        if (intensity <= 0f || lights.isEmpty()) {
            return;
        }
        float sr = 0, sg = 0, sb = 0;
        for (Light light : lights) {
            float dx = light.position.x - position.x;
            float dy = light.position.y - position.y;
            float dz = light.position.z - position.z;
            float d2 = dx * dx + dy * dy + dz * dz;
            float att = attenuation(d2, light.range);
            if (att <= 0f) {
                continue;
            }
            if (light.spot) {
                float inv = (float) (1.0 / Math.sqrt(Math.max(d2, 1e-6f)));
                float cos = -(dx * inv * light.axis.x + dy * inv * light.axis.y + dz * inv * light.axis.z);
                att *= smoothstep(light.cosOuter, light.cosInner, cos);
                if (att <= 0f) {
                    continue;
                }
            }
            sr += light.r * att;
            sg += light.g * att;
            sb += light.b * att;
        }
        float peak = Math.max(sr, Math.max(sg, sb));
        if (peak <= 0f) {
            return;
        }
        float k = intensity * (1f - (float) Math.exp(-peak)) / peak;
        out[0] = sr * k;
        out[1] = sg * k;
        out[2] = sb * k;
    }

    public void drawGround(Graphics2D g2, int width, int height, PreviewCamera camera, int baseRgb) {
        if (lights.isEmpty()) {
            return;
        }
        Vec3 cam = camera.position();
        if (cam.y <= 0.001f) {
            return;
        }
        Vec3 fwd = camera.forward();
        Vec3 right = camera.right();
        Vec3 up = camera.up();
        float focal = camera.focalLength();
        int step = Math.max(6, (int) Math.ceil(Math.sqrt((double) width * height / 16000.0)));
        int gw = (width + step - 1) / step;
        int gh = (height + step - 1) / step;
        int[] pixels = new int[gw * gh];
        float[] scene = {((baseRgb >> 16) & 0xFF) / 255f, ((baseRgb >> 8) & 0xFF) / 255f, (baseRgb & 0xFF) / 255f};
        final int fStep = step;
        java.util.stream.IntStream.range(0, gh).parallel().forEach(gy -> {
            float py = (gy + 0.5f) * fStep;
            for (int gx = 0; gx < gw; gx++) {
                float px = (gx + 0.5f) * fStep;
                float dx = fwd.x * focal + right.x * (px - width / 2f) + up.x * (height / 2f - py);
                float dy = fwd.y * focal + right.y * (px - width / 2f) + up.y * (height / 2f - py);
                float dz = fwd.z * focal + right.z * (px - width / 2f) + up.z * (height / 2f - py);
                if (dy >= -1e-6f) {
                    continue;
                }
                float t = -cam.y / dy;
                float gxw = cam.x + dx * t;
                float gzw = cam.z + dz * t;
                if (Math.abs(gxw) > 400f || Math.abs(gzw) > 400f) {
                    continue;
                }
                float ir = 0, ig = 0, ib = 0;
                for (Light light : lights) {
                    float lx = light.position.x - gxw;
                    float ly = light.position.y;
                    float lz = light.position.z - gzw;
                    float d2 = lx * lx + ly * ly + lz * lz;
                    float att = attenuation(d2, light.range);
                    if (att <= 0f || ly <= 0f) {
                        continue;
                    }
                    float inv = (float) (1.0 / Math.sqrt(Math.max(d2, 1e-6f)));
                    float ndl = ly * inv;
                    if (light.spot) {
                        float cos = -(lx * inv * light.axis.x + ly * inv * light.axis.y + lz * inv * light.axis.z);
                        att *= smoothstep(light.cosOuter, light.cosInner, cos);
                    }
                    float w = att * ndl;
                    ir += light.r * w;
                    ig += light.g * w;
                    ib += light.b * w;
                }
                if (ir <= 0f && ig <= 0f && ib <= 0f) {
                    continue;
                }
                float or = Math.max(0f, 1f - (1f - scene[0]) * (float) Math.exp(-GROUND_ALBEDO * ir) - scene[0]);
                float og = Math.max(0f, 1f - (1f - scene[1]) * (float) Math.exp(-GROUND_ALBEDO * ig) - scene[1]);
                float ob = Math.max(0f, 1f - (1f - scene[2]) * (float) Math.exp(-GROUND_ALBEDO * ib) - scene[2]);
                if (or + og + ob < 0.004f) {
                    continue;
                }
                pixels[gy * gw + gx] = 0xFF000000 | (to255(or) << 16) | (to255(og) << 8) | to255(ob);
            }
        });
        boolean any = false;
        for (int px : pixels) {
            if (px != 0) {
                any = true;
                break;
            }
        }
        if (!any) {
            return;
        }
        BufferedImage img = new BufferedImage(gw, gh, BufferedImage.TYPE_INT_RGB);
        img.setRGB(0, 0, gw, gh, pixels, 0, gw);
        Composite oldComposite = g2.getComposite();
        Object oldInterp = g2.getRenderingHint(RenderingHints.KEY_INTERPOLATION);
        try {
            g2.setComposite(plainAdd);
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g2.drawImage(img, 0, 0, gw * step, gh * step, null);
        } finally {
            g2.setComposite(oldComposite);
            if (oldInterp != null) {
                g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, oldInterp);
            }
        }
    }

    public void drawHaze(Graphics2D g2, int width, int height, PreviewCamera camera) {
        if (volumetric.isEmpty() && fogs.isEmpty()) {
            return;
        }
        Vec3 cam = camera.position();
        Vec3 fwd = camera.forward();
        Vec3 right = camera.right();
        Vec3 up = camera.up();
        float focal = camera.focalLength();
        int step = Math.max(8, (int) Math.ceil(Math.sqrt((double) width * height / 9000.0)));
        int gw = (width + step - 1) / step;
        int gh = (height + step - 1) / step;
        int[] hazePixels = new int[gw * gh];
        int[] transPixels = new int[gw * gh];
        List<Fog> orderedFogs = new ArrayList<>(fogs);
        orderedFogs.sort(Comparator.comparingDouble(f -> distanceSq(f.center, cam)));
        final int fStep = step;
        java.util.stream.IntStream.range(0, gh).parallel().forEach(gy -> {
            float[] sum = new float[3];
            float[] scratch = new float[3];
            float py = (gy + 0.5f) * fStep;
            for (int gx = 0; gx < gw; gx++) {
                float px = (gx + 0.5f) * fStep;
                float dx = fwd.x * focal + right.x * (px - width / 2f) + up.x * (height / 2f - py);
                float dy = fwd.y * focal + right.y * (px - width / 2f) + up.y * (height / 2f - py);
                float dz = fwd.z * focal + right.z * (px - width / 2f) + up.z * (height / 2f - py);
                float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
                dx /= len;
                dy /= len;
                dz /= len;
                float rayEnd = cam.y > 0f && dy < -1e-6f ? -cam.y / dy : 1e9f;
                sum[0] = sum[1] = sum[2] = 0f;
                for (Light light : volumetric) {
                    scatterLight(light, cam, dx, dy, dz, rayEnd, sum);
                }
                float transmittance = 1f;
                for (Fog fog : orderedFogs) {
                    if (transmittance <= 1e-3f) {
                        break;
                    }
                    transmittance = marchFog(fog, cam, dx, dy, dz, rayEnd, transmittance, sum, scratch);
                }
                int i = gy * gw + gx;
                if (sum[0] > 1e-4f || sum[1] > 1e-4f || sum[2] > 1e-4f) {
                    hazePixels[i] = 0xFF000000
                            | (to255(1f - (float) Math.exp(-Math.min(sum[0], 3e4f))) << 16)
                            | (to255(1f - (float) Math.exp(-Math.min(sum[1], 3e4f))) << 8)
                            | to255(1f - (float) Math.exp(-Math.min(sum[2], 3e4f)));
                } else {
                    hazePixels[i] = 0xFF000000;
                }
                int tv = to255(transmittance);
                transPixels[i] = 0xFF000000 | (tv << 16) | (tv << 8) | tv;
            }
        });
        boolean anyHaze = false;
        boolean anyFog = false;
        for (int i = 0; i < hazePixels.length; i++) {
            if (hazePixels[i] != 0xFF000000) {
                anyHaze = true;
            }
            if ((transPixels[i] & 0xFF) < 253) {
                anyFog = true;
            }
        }
        Composite oldComposite = g2.getComposite();
        Object oldInterp = g2.getRenderingHint(RenderingHints.KEY_INTERPOLATION);
        try {
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            if (anyFog) {
                BufferedImage trans = new BufferedImage(gw, gh, BufferedImage.TYPE_INT_RGB);
                trans.setRGB(0, 0, gw, gh, transPixels, 0, gw);
                g2.setComposite(multiply);
                g2.drawImage(trans, 0, 0, gw * step, gh * step, null);
            }
            if (anyHaze) {
                BufferedImage haze = new BufferedImage(gw, gh, BufferedImage.TYPE_INT_RGB);
                haze.setRGB(0, 0, gw, gh, hazePixels, 0, gw);
                g2.setComposite(softAdd);
                g2.drawImage(haze, 0, 0, gw * step, gh * step, null);
            }
        } finally {
            g2.setComposite(oldComposite);
            if (oldInterp != null) {
                g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, oldInterp);
            }
        }
    }

    private void scatterLight(Light light, Vec3 o, float dx, float dy, float dz, float rayEnd, float[] sum) {
        float rx = light.position.x - o.x;
        float ry = light.position.y - o.y;
        float rz = light.position.z - o.z;
        float b = rx * dx + ry * dy + rz * dz;
        float h2 = Math.max(rx * rx + ry * ry + rz * rz - b * b, 0f);
        float range2 = light.range * light.range;
        if (h2 >= range2) {
            return;
        }
        float halfChord = (float) Math.sqrt(range2 - h2);
        float t0 = Math.max(0f, b - halfChord);
        float t1 = Math.min(rayEnd, b + halfChord);
        if (t1 <= t0) {
            return;
        }
        boolean spot = light.spot;
        if (spot && light.cosOuter > 0.087f) {
            float[] cone = coneRange(o, dx, dy, dz, light.position, light.axis, light.cosOuter);
            t0 = Math.max(t0, cone[0]);
            t1 = Math.min(t1, cone[1]);
        }
        if (t1 <= t0) {
            return;
        }
        float g = FORWARD_SCATTERING;
        float h = (float) Math.sqrt(h2 + 1f);
        float shift = 1f / (range2 + 1f);
        float u0 = t0 - b;
        float u1 = t1 - b;
        int n = spot ? VOLUME_PIECES * 2 : VOLUME_PIECES;
        float phi0 = (float) Math.atan(u0 / h);
        float phi1 = (float) Math.atan(u1 / h);
        float total = 0f;
        for (int side = 0; side < 2; side++) {
            float a = side == 0 ? u0 : Math.max(u0, 0f);
            float z = side == 0 ? Math.min(u1, 0f) : u1;
            if (z <= a) {
                continue;
            }
            float stepAngle = ((side == 0 ? Math.min(phi1, 0f) : phi1) - (side == 0 ? phi0 : Math.max(phi0, 0f))) / n;
            float tanStep = (float) Math.tan(stepAngle);
            float tanOffset = (float) Math.tan(0.5f * stepAngle);
            float tanA = a / h;
            for (int i = 0; i < n; i++) {
                float tanB = i == n - 1 ? z / h : (tanA + tanStep) / (1f - tanA * tanStep);
                float piece = stepAngle / h - shift * h * (tanB - tanA);
                float um = h * (tanA + tanOffset) / (1f - tanA * tanOffset);
                tanA = tanB;
                float rm = Math.max((float) Math.sqrt(um * um + h2), 1e-4f);
                float factor = phase(-um / rm, g);
                if (spot) {
                    float px = (b + um) * dx - rx;
                    float py = (b + um) * dy - ry;
                    float pz = (b + um) * dz - rz;
                    float cos = (px * light.axis.x + py * light.axis.y + pz * light.axis.z) / rm;
                    factor *= smoothstep(light.cosOuter, light.cosInner, cos);
                }
                total += Math.max(piece, 0f) * factor;
            }
        }
        float scale = light.volumetric * VOLUME_SCALE * total;
        sum[0] += light.r * scale;
        sum[1] += light.g * scale;
        sum[2] += light.b * scale;
    }

    private float marchFog(Fog fog, Vec3 o, float dx, float dy, float dz, float rayEnd, float transmittance,
                           float[] sum, float[] scratch) {
        float cx = fog.center.x - o.x;
        float cy = fog.center.y - o.y;
        float cz = fog.center.z - o.z;
        float bb = cx * dx + cy * dy + cz * dz;
        float h2 = cx * cx + cy * cy + cz * cz - bb * bb;
        if (h2 > fog.boundRadius * fog.boundRadius) {
            return transmittance;
        }
        Vec3 lo = fog.worldToLocal.transformPosition(o);
        Vec3 ld = fog.worldToLocal.transformDirection(new Vec3(dx, dy, dz));
        float rangeX;
        float rangeY;
        if (fog.sphere) {
            float a = ld.dot(ld);
            float bq = lo.dot(ld);
            float c = lo.dot(lo) - 0.25f;
            float disc = bq * bq - a * c;
            if (disc < 0f) {
                return transmittance;
            }
            float s = (float) Math.sqrt(disc);
            rangeX = (-bq - s) / a;
            rangeY = (-bq + s) / a;
        } else {
            float ix = 1f / (Math.abs(ld.x) < 1e-8f ? 1e-8f : ld.x);
            float iy = 1f / (Math.abs(ld.y) < 1e-8f ? 1e-8f : ld.y);
            float iz = 1f / (Math.abs(ld.z) < 1e-8f ? 1e-8f : ld.z);
            float ax = (-0.5f - lo.x) * ix;
            float bx = (0.5f - lo.x) * ix;
            float ay = (-0.5f - lo.y) * iy;
            float by = (0.5f - lo.y) * iy;
            float az = (-0.5f - lo.z) * iz;
            float bz = (0.5f - lo.z) * iz;
            rangeX = Math.max(Math.min(ax, bx), Math.max(Math.min(ay, by), Math.min(az, bz)));
            rangeY = Math.min(Math.max(ax, bx), Math.min(Math.max(ay, by), Math.max(az, bz)));
        }
        float t0 = Math.max(rangeX, 0f);
        float t1 = Math.min(rangeY, rayEnd);
        if (t1 <= t0) {
            return transmittance;
        }
        float dt = (t1 - t0) / FOG_STEPS;
        float g = FORWARD_SCATTERING;
        for (int i = 0; i < FOG_STEPS && transmittance > 1e-3f; i++) {
            float t = t0 + (i + 0.5f) * dt;
            float lx = lo.x + t * ld.x;
            float ly = lo.y + t * ld.y;
            float lz = lo.z + t * ld.z;
            float inside = fog.sphere
                    ? 1f - 2f * (float) Math.sqrt(lx * lx + ly * ly + lz * lz)
                    : 1f - 2f * Math.max(Math.abs(lx), Math.max(Math.abs(ly), Math.abs(lz)));
            float density = fog.density * FOG_SCALE * smoothstep(0f, Math.max(fog.edgeFalloff, 1e-3f), inside);
            if (fog.noise > 0f) {
                float qx = (lx * fog.size.x + fog.drift.x) / fog.noiseScale;
                float qy = (ly * fog.size.y + fog.drift.y) / fog.noiseScale;
                float qz = (lz * fog.size.z + fog.drift.z) / fog.noiseScale;
                float n = 0.65f * valueNoise(qx, qy, qz) + 0.35f * valueNoise(qx * 2.03f + 17f, qy * 2.03f + 17f, qz * 2.03f + 17f);
                density *= Math.max(0f, Math.min(2f, 1f + fog.noise * (2f * n - 1f)));
            }
            if (density <= 0f) {
                continue;
            }
            float px = o.x + t * dx;
            float py = o.y + t * dy;
            float pz = o.z + t * dz;
            inscatter(px, py, pz, dx, dy, dz, g, scratch);
            float lr = fog.color[0] * scratch[0] + fog.emission[0];
            float lg = fog.color[1] * scratch[1] + fog.emission[1];
            float lb = fog.color[2] * scratch[2] + fog.emission[2];
            float extinction = density * fog.absorption;
            float through = (float) Math.exp(-extinction * dt);
            float k = transmittance * density * (extinction > 1e-5f ? (1f - through) / extinction : dt);
            sum[0] += k * lr;
            sum[1] += k * lg;
            sum[2] += k * lb;
            transmittance *= through;
        }
        return transmittance;
    }

    private void inscatter(float px, float py, float pz, float dx, float dy, float dz, float g, float[] out) {
        out[0] = out[1] = out[2] = 0f;
        for (Light light : lights) {
            float lx = light.position.x - px;
            float ly = light.position.y - py;
            float lz = light.position.z - pz;
            float d2 = lx * lx + ly * ly + lz * lz;
            float att = attenuation(d2, light.range);
            if (att <= 0f) {
                continue;
            }
            float inv = (float) (1.0 / Math.sqrt(Math.max(d2, 1e-6f)));
            float nx = lx * inv;
            float ny = ly * inv;
            float nz = lz * inv;
            if (light.spot) {
                float cos = -(nx * light.axis.x + ny * light.axis.y + nz * light.axis.z);
                att *= smoothstep(light.cosOuter, light.cosInner, cos);
                if (att <= 0f) {
                    continue;
                }
            }
            float w = att * phase(nx * dx + ny * dy + nz * dz, g);
            out[0] += light.r * w;
            out[1] += light.g * w;
            out[2] += light.b * w;
        }
    }


    public static final class Light {
        public final Vec3 position;
        public final float r;
        public final float g;
        public final float b;
        public final float range;
        public final boolean spot;
        public final Vec3 axis;
        public final float cosOuter;
        public final float cosInner;
        public final float volumetric;

        private Light(Vec3 position, float r, float g, float b, float range, boolean spot, Vec3 axis, float cosOuter,
                      float cosInner, float volumetric) {
            this.position = position;
            this.r = Math.max(0f, r);
            this.g = Math.max(0f, g);
            this.b = Math.max(0f, b);
            this.range = range;
            this.spot = spot;
            this.axis = axis;
            this.cosOuter = cosOuter;
            this.cosInner = cosInner;
            this.volumetric = volumetric;
        }

        public static Light of(Vec3 position, float r, float g, float b, float range, boolean spot, Vec3 axis,
                               float innerDegrees, float outerDegrees, float volumetric) {
            float outer = Math.max(0.1f, Math.min(90f, outerDegrees));
            float inner = Math.max(0f, Math.min(innerDegrees, outer));
            Vec3 a = new Vec3(axis);
            if (a.lengthSquared() > 1e-12f) {
                a.normalize();
            } else {
                a.set(0, -1, 0);
            }
            return new Light(new Vec3(position), r, g, b, range, spot, a,
                    (float) Math.cos(Math.toRadians(outer)), (float) Math.cos(Math.toRadians(inner)), volumetric);
        }
    }

    public static final class Fog {
        final boolean sphere;
        final Vec3 center;
        final Mat4 worldToLocal;
        final Vec3 size;
        final Vec3 drift;
        final float density;
        final float edgeFalloff;
        final float absorption;
        final float[] color;
        final float[] emission;
        final float noise;
        final float noiseScale;
        final float boundRadius;

        private Fog(boolean sphere, Vec3 center, Mat4 worldToLocal, Vec3 size, Vec3 drift, float density,
                    float edgeFalloff, float absorption, float[] color, float[] emission, float noise,
                    float noiseScale) {
            this.sphere = sphere;
            this.center = center;
            this.worldToLocal = worldToLocal;
            this.size = size;
            this.drift = drift;
            this.density = density;
            this.edgeFalloff = Math.max(0f, Math.min(1f, edgeFalloff));
            this.absorption = Math.max(0f, Math.min(1f, absorption));
            this.color = new float[]{Math.max(0f, color[0]), Math.max(0f, color[1]), Math.max(0f, color[2])};
            this.emission = new float[]{Math.max(0f, emission[0]), Math.max(0f, emission[1]), Math.max(0f, emission[2])};
            this.noise = Math.max(0f, Math.min(1f, noise));
            this.noiseScale = Math.max(noiseScale, 0.05f);
            this.boundRadius = 0.5f * size.length();
        }

        public static Fog of(boolean sphere, Vec3 center, Mat4 worldToLocal, Quat rotation, Vec3 scale, float density,
                             float edgeFalloff, float absorption, float[] color, float[] emission, float noise,
                             float noiseScale, Vec3 wind) {
            Vec3 ax = rotation.rotate(new Vec3(1, 0, 0));
            Vec3 ay = rotation.rotate(new Vec3(0, 1, 0));
            Vec3 az = rotation.rotate(new Vec3(0, 0, 1));
            Vec3 drift = new Vec3(ax.dot(wind), ay.dot(wind), az.dot(wind));
            return new Fog(sphere, new Vec3(center), worldToLocal, new Vec3(scale), drift, density, edgeFalloff,
                    absorption, color, emission, noise, noiseScale);
        }

        public Vec3 center() {
            return center;
        }

        public boolean isSphere() {
            return sphere;
        }

        public Vec3 size() {
            return size;
        }
    }
}
