package com.spatialcoach.soundarchitecture.stage

import com.pico.spatial.core.ecs.BoundingBox
import com.pico.spatial.core.ecs.resource.MeshModel
import com.pico.spatial.core.math.Color4
import com.pico.spatial.core.math.Vector2
import com.pico.spatial.core.math.Vector3
import kotlin.math.cos
import kotlin.math.sin

/** 舞台用的简单网格：扇区膜面、细棒和圆盘。不是柱阵。 */
object SoundMeshes {

    /**
     * 单位扇区：角度跨度 [angleSpan] 弧度，内径 1、外径 2，高度 1，底面在 y=0。
     * 用缩放表达实际半径和高度。
     */
    fun unitWedge(angleSpan: Float): MeshModel {
        val half = angleSpan / 2f
        val inner = 1f
        val outer = 2f
        val positions = ArrayList<Vector3>()
        val normals = ArrayList<Vector3>()
        val colors = ArrayList<Color4>()
        val uvs = ArrayList<Vector2>()
        val indices = ArrayList<Int>()
        val white = Color4(1f, 1f, 1f, 1f)

        val corners = arrayOf(
            vec(half, inner, 0f),
            vec(-half, inner, 0f),
            vec(-half, outer, 0f),
            vec(half, outer, 0f),
            vec(half, inner, 1f),
            vec(-half, inner, 1f),
            vec(-half, outer, 1f),
            vec(half, outer, 1f),
        )

        fun quad(a: Int, b: Int, c: Int, d: Int, normal: Vector3, uvA: Vector2, uvB: Vector2, uvC: Vector2, uvD: Vector2) {
            val base = positions.size
            val pts = arrayOf(corners[a], corners[b], corners[c], corners[d])
            val u = arrayOf(uvA, uvB, uvC, uvD)
            for (i in 0..3) {
                positions.add(pts[i])
                normals.add(normal)
                colors.add(white)
                uvs.add(u[i])
            }
            indices.add(base)
            indices.add(base + 1)
            indices.add(base + 2)
            indices.add(base)
            indices.add(base + 2)
            indices.add(base + 3)
        }

        val up = Vector3(0f, 1f, 0f)
        val down = Vector3(0f, -1f, 0f)
        // 顶面：从 +Y 往下看为逆时针
        quad(4, 5, 6, 7, up, Vector2(0f, 0f), Vector2(1f, 0f), Vector2(1f, 1f), Vector2(0f, 1f))
        // 底面
        quad(0, 3, 2, 1, down, Vector2(0f, 0f), Vector2(1f, 0f), Vector2(1f, 1f), Vector2(0f, 1f))
        // 外侧弧（近似成一个平面，法线指向外径中点）
        val outN = normalize(vec(0f, 1f, 0f))
        quad(3, 7, 6, 2, Vector3(outN.x, 0f, outN.z), Vector2(0f, 0f), Vector2(0f, 1f), Vector2(1f, 1f), Vector2(1f, 0f))
        val inN = normalize(vec(0f, -1f, 0f))
        quad(1, 5, 4, 0, Vector3(inN.x, 0f, inN.z), Vector2(0f, 0f), Vector2(0f, 1f), Vector2(1f, 1f), Vector2(1f, 0f))
        val leftN = Vector3(-cos(0f), 0f, -sin(0f)) // placeholder, replaced below
        // 左右侧面
        val leftMid = mid(corners[1], corners[2])
        val rightMid = mid(corners[0], corners[3])
        quad(1, 2, 6, 5, sideNormal(-half), Vector2(0f, 0f), Vector2(1f, 0f), Vector2(1f, 1f), Vector2(0f, 1f))
        quad(0, 4, 7, 3, sideNormal(half), Vector2(0f, 0f), Vector2(0f, 1f), Vector2(1f, 1f), Vector2(1f, 0f))

        return MeshModel(
            positions = positions,
            triangleIndices = indices,
            normals = normals,
            uv0 = uvs,
            colors = colors,
        )
    }

    /** 细棒：沿 +Y，底在原点，高 1，截面是边长 1 的方形。 */
    fun unitRod(): MeshModel {
        val positions = ArrayList<Vector3>()
        val normals = ArrayList<Vector3>()
        val colors = ArrayList<Color4>()
        val uvs = ArrayList<Vector2>()
        val indices = ArrayList<Int>()
        val white = Color4(1f, 1f, 1f, 1f)
        val h = 0.5f
        val faces = listOf(
            Triple(Vector3(1f, 0f, 0f), Vector3(0f, 0f, 0.5f), Vector3(0f, 0.5f, 0f)),
            Triple(Vector3(-1f, 0f, 0f), Vector3(0f, 0f, -0.5f), Vector3(0f, 0.5f, 0f)),
            Triple(Vector3(0f, 0f, 1f), Vector3(-0.5f, 0f, 0f), Vector3(0f, 0.5f, 0f)),
            Triple(Vector3(0f, 0f, -1f), Vector3(0.5f, 0f, 0f), Vector3(0f, 0.5f, 0f)),
        )
        for ((n, u, v) in faces) {
            val c = Vector3(n.x * h, 0.5f, n.z * h)
            addQuad(positions, normals, colors, uvs, indices, c, u, v, n, white)
        }
        // 顶盖
        addQuad(
            positions, normals, colors, uvs, indices,
            c = Vector3(0f, 1f, 0f),
            u = Vector3(h, 0f, 0f),
            v = Vector3(0f, 0f, h),
            n = Vector3(0f, 1f, 0f),
            color = white,
        )
        return MeshModel(
            positions = positions,
            triangleIndices = indices,
            normals = normals,
            uv0 = uvs,
            colors = colors,
        )
    }

    fun disc(radius: Float, segments: Int = 48): MeshModel {
        val positions = ArrayList<Vector3>()
        val normals = ArrayList<Vector3>()
        val colors = ArrayList<Color4>()
        val uvs = ArrayList<Vector2>()
        val indices = ArrayList<Int>()
        val white = Color4(1f, 1f, 1f, 1f)
        val up = Vector3(0f, 1f, 0f)
        positions.add(Vector3(0f, 0f, 0f))
        normals.add(up)
        colors.add(white)
        uvs.add(Vector2(0.5f, 0.5f))
        for (i in 0..segments) {
            val a = (i.toFloat() / segments) * (Math.PI * 2).toFloat()
            val x = cos(a) * radius
            val z = sin(a) * radius
            positions.add(Vector3(x, 0f, z))
            normals.add(up)
            colors.add(white)
            uvs.add(Vector2(0.5f + 0.5f * cos(a), 0.5f + 0.5f * sin(a)))
            if (i > 0) {
                indices.add(0)
                indices.add(i)
                indices.add(i + 1)
            }
        }
        return MeshModel(
            positions = positions,
            triangleIndices = indices,
            normals = normals,
            uv0 = uvs,
            colors = colors,
        )
    }

    fun boundsOf(positions: List<Vector3>): BoundingBox {
        if (positions.isEmpty()) {
            return BoundingBox(center = Vector3(0f, 0f, 0f), halfExtent = Vector3(0.01f, 0.01f, 0.01f))
        }
        var minX = Float.POSITIVE_INFINITY
        var minY = Float.POSITIVE_INFINITY
        var minZ = Float.POSITIVE_INFINITY
        var maxX = Float.NEGATIVE_INFINITY
        var maxY = Float.NEGATIVE_INFINITY
        var maxZ = Float.NEGATIVE_INFINITY
        for (p in positions) {
            if (p.x < minX) minX = p.x
            if (p.y < minY) minY = p.y
            if (p.z < minZ) minZ = p.z
            if (p.x > maxX) maxX = p.x
            if (p.y > maxY) maxY = p.y
            if (p.z > maxZ) maxZ = p.z
        }
        return BoundingBox(
            center = Vector3((minX + maxX) / 2f, (minY + maxY) / 2f, (minZ + maxZ) / 2f),
            halfExtent = Vector3((maxX - minX) / 2f, (maxY - minY) / 2f, (maxZ - minZ) / 2f),
        )
    }

    private fun vec(angle: Float, radius: Float, y: Float): Vector3 {
        // 0 弧度指向 -Z（用户前方），正角度向右（+X）
        return Vector3(sin(angle) * radius, y, -cos(angle) * radius)
    }

    private fun mid(a: Vector3, b: Vector3) =
        Vector3((a.x + b.x) / 2f, (a.y + b.y) / 2f, (a.z + b.z) / 2f)

    private fun normalize(v: Vector3): Vector3 {
        val len = kotlin.math.sqrt(v.x * v.x + v.y * v.y + v.z * v.z)
        return if (len < 1e-6f) v else Vector3(v.x / len, v.y / len, v.z / len)
    }

    private fun sideNormal(angle: Float): Vector3 {
        // 侧面朝向：垂直于该半径方向、绕 Y 向外
        val radial = vec(angle, 1f, 0f)
        return normalize(Vector3(radial.x, 0f, radial.z).let {
            // 切向 × 上 = 指向外侧的近似：用半径方向在 XZ 上的指向
            Vector3(it.x, 0f, it.z)
        })
    }

    private fun addQuad(
        positions: ArrayList<Vector3>,
        normals: ArrayList<Vector3>,
        colors: ArrayList<Color4>,
        uvs: ArrayList<Vector2>,
        indices: ArrayList<Int>,
        c: Vector3,
        u: Vector3,
        v: Vector3,
        n: Vector3,
        color: Color4,
    ) {
        val base = positions.size
        val pts = arrayOf(
            Vector3(c.x - u.x - v.x, c.y - u.y - v.y, c.z - u.z - v.z),
            Vector3(c.x + u.x - v.x, c.y + u.y - v.y, c.z + u.z - v.z),
            Vector3(c.x + u.x + v.x, c.y + u.y + v.y, c.z + u.z + v.z),
            Vector3(c.x - u.x + v.x, c.y - u.y + v.y, c.z - u.z + v.z),
        )
        val uv = arrayOf(Vector2(0f, 0f), Vector2(1f, 0f), Vector2(1f, 1f), Vector2(0f, 1f))
        for (i in 0..3) {
            positions.add(pts[i])
            normals.add(n)
            colors.add(color)
            uvs.add(uv[i])
        }
        indices.add(base)
        indices.add(base + 1)
        indices.add(base + 2)
        indices.add(base)
        indices.add(base + 2)
        indices.add(base + 3)
    }
}
