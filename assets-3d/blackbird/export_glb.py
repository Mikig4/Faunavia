"""Deterministic glTF 2 exporter for this original untextured, rigid-node study only."""
import json
import math
import struct


def write_glb(path, objects):
    binary = bytearray()
    views, accessors, meshes, materials, nodes = [], [], [], [], []

    def floats(values):
        # Fixed authoring precision, followed by exact IEEE-754 values for accessor limits.
        return [struct.unpack("<f", struct.pack("<f", round(float(v), 6)))[0] for v in values]

    def accessor(values, width, kind, indices=False, bounds=False):
        values = list(values) if indices else floats(values)
        while len(binary) % 4:
            binary.append(0)
        start = len(binary)
        binary.extend(struct.pack("<" + ("H" if indices else "f") * len(values), *values))
        views.append({"buffer": 0, "byteOffset": start, "byteLength": len(binary) - start})
        entry = {"bufferView": len(views) - 1, "componentType": 5123 if indices else 5126,
                 "count": len(values) // width, "type": kind}
        if bounds:
            entry["min"] = [min(values[i::width]) for i in range(width)]
            entry["max"] = [max(values[i::width]) for i in range(width)]
        accessors.append(entry)
        return len(accessors) - 1

    material_ids = {}
    for obj in objects:
        node = {"name": obj.name}
        translation, rotation, scale = obj.matrix_basis.decompose()
        node["translation"] = floats(translation)
        node["rotation"] = floats((rotation.x, rotation.y, rotation.z, rotation.w))
        node["scale"] = floats(scale)
        children = [objects.index(child) for child in objects if child.parent == obj]
        if children:
            node["children"] = children
        if obj.type == "MESH":
            mat = obj.data.materials[0]
            if mat.name not in material_ids:
                shader = mat.node_tree.nodes.get("Principled BSDF")
                material_ids[mat.name] = len(materials)
                materials.append({"name": mat.name, "pbrMetallicRoughness": {
                    "baseColorFactor": floats(shader.inputs["Base Color"].default_value),
                    "metallicFactor": 0, "roughnessFactor": round(shader.inputs["Roughness"].default_value, 6)}})
            positions, normals, indices = [], [], []
            vertex_ids = {}
            # Every polygon was triangulated in the .blend authoring source.
            def face_key(face):
                return (tuple(floats(face.normal)), tuple(sorted(tuple(floats(obj.data.vertices[v].co)) for v in face.vertices)))
            for face in sorted(obj.data.polygons, key=face_key):
                assert len(face.vertices) == 3
                normal = floats(face.normal)
                ordered = list(face.vertices)
                first = min(range(3), key=lambda i: tuple(floats(obj.data.vertices[ordered[i]].co)))
                ordered = ordered[first:] + ordered[:first]
                for vertex in ordered:
                    position = floats(obj.data.vertices[vertex].co)
                    key = tuple(position + normal)
                    if key not in vertex_ids:
                        vertex_ids[key] = len(vertex_ids)
                        positions.extend(position)
                        normals.extend(normal)
                    indices.append(vertex_ids[key])
            primitive = {"attributes": {"POSITION": accessor(positions, 3, "VEC3", bounds=True),
                                         "NORMAL": accessor(normals, 3, "VEC3")},
                         "indices": accessor(indices, 1, "SCALAR", indices=True), "material": material_ids[mat.name]}
            node["mesh"] = len(meshes)
            meshes.append({"name": obj.name, "primitives": [primitive]})
        nodes.append(node)

    # Explicit root rotation gives glTF metres/Y-up while preserving the authored local animation axes.
    nodes.append({"name": "Metres Y up", "rotation": [-math.sqrt(.5), 0, 0, math.sqrt(.5)],
                  "children": [i for i, obj in enumerate(objects) if obj.parent is None]})
    animations = []
    for name, object_name, channel, times, output, width, kind in (
        ("Respiro", "Merlo", "scale", [0, 1, 2], [1, 1, 1, 1.015, 1.005, 1.015, 1, 1, 1], 3, "VEC3"),
        ("Guarda intorno", "Testa", "rotation", [0, 1, 2, 3, 4],
         [v for angle in [0, .20, 0, -.20, 0] for v in [0, 0, math.sin(angle / 2), math.cos(angle / 2)]], 4, "VEC4"),
    ):
        animations.append({"name": name,
            "samplers": [{"input": accessor(times, 1, "SCALAR", bounds=True), "output": accessor(output, width, kind), "interpolation": "LINEAR"}],
            "channels": [{"sampler": 0, "target": {"node": next(i for i, obj in enumerate(objects) if obj.name == object_name), "path": channel}}]})
    length = len(binary)
    while len(binary) % 4:
        binary.append(0)
    root = {"asset": {"version": "2.0", "generator": "Faunavia deterministic original-study exporter v1"},
            "scene": 0, "scenes": [{"nodes": [len(nodes) - 1]}], "nodes": nodes, "meshes": meshes,
            "materials": materials, "animations": animations, "buffers": [{"byteLength": length}],
            "bufferViews": views, "accessors": accessors}
    description = json.dumps(root, sort_keys=True, separators=(",", ":"), ensure_ascii=False).encode("utf-8")
    description += b" " * (-len(description) % 4)
    path.write_bytes(struct.pack("<III", 0x46546C67, 2, 28 + len(description) + len(binary)) +
                     struct.pack("<II", len(description), 0x4E4F534A) + description +
                     struct.pack("<II", len(binary), 0x004E4942) + binary)
