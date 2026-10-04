"""The vector and quaternion arithmetic of the model tools: 3-tuples (x, y, z) and quaternions (x, y, z, w). Written
out term by term, in a fixed order: the generators' outputs are compared bit for bit."""

import math
import operator


def add(a, b):
    return tuple(map(operator.add, a, b))


def multiply(q, r):
    x1, y1, z1, w1 = q
    x2, y2, z2, w2 = r
    return (w1 * x2 + x1 * w2 + y1 * z2 - z1 * y2,
            w1 * y2 - x1 * z2 + y1 * w2 + z1 * x2,
            w1 * z2 + x1 * y2 - y1 * x2 + z1 * w2,
            w1 * w2 - x1 * x2 - y1 * y2 - z1 * z2)


def rotate(q, v):
    """v turned by the quaternion q (normalised first): q * v * conjugate(q), written out term by term in the order
    multiply computes them (the generators' hot path; the same floats as two multiply calls)."""
    a, b, c, w = q
    # sum, not +: Python's float sum is compensated, and the generators' outputs are compared bit for bit.
    norm = math.sqrt(sum((a * a, b * b, c * c, w * w))) or 1.0
    a, b, c, w = a / norm, b / norm, c / norm, w / norm
    vx, vy, vz = v
    px = w * vx + a * 0.0 + b * vz - c * vy
    py = w * vy - a * vz + b * 0.0 + c * vx
    pz = w * vz + a * vy - b * vx + c * 0.0
    pw = w * 0.0 - a * vx - b * vy - c * vz
    return (pw * -a + px * w + py * -c - pz * -b,
            pw * -b - px * -c + py * w + pz * -a,
            pw * -c + px * -b - py * -a + pz * w)


def sub(a, b):
    return (a[0] - b[0], a[1] - b[1], a[2] - b[2])


def scale(a, k):
    return (a[0] * k, a[1] * k, a[2] * k)


def dot(a, b):
    # sum, not +: Python's float sum is compensated, and the generators' outputs are compared bit for bit.
    return sum((a[0] * b[0], a[1] * b[1], a[2] * b[2]))


def cross(a, b):
    return (a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0])


def length(a):
    return math.sqrt(dot(a, a))


def unit(a):
    size = length(a)
    return scale(a, 1 / size) if size > 1e-9 else a


def near(points_a, points_b, reach):
    """Whether two sets of points come within reach of each other (bounding boxes, widened by reach)."""
    lo_a, hi_a = [min(p[k] for p in points_a) for k in range(3)], [max(p[k] for p in points_a) for k in range(3)]
    lo_b, hi_b = [min(p[k] for p in points_b) for k in range(3)], [max(p[k] for p in points_b) for k in range(3)]
    return all(lo_a[k] - reach <= hi_b[k] and lo_b[k] - reach <= hi_a[k] for k in range(3))
