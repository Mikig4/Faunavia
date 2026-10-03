"""Original procedural male blackbird study. Blender 4.5.3, no imported geometry/images."""
import bpy
import math
import pathlib
import sys

root = pathlib.Path(__file__).resolve().parents[2]
folder = root / "assets-3d" / "blackbird"
delivery = root / "app" / "src" / "main" / "assets" / "models"
delivery.mkdir(parents=True, exist_ok=True)
bpy.ops.object.select_all(action="SELECT")
bpy.ops.object.delete(use_global=False)

def material(name, color, roughness=0.8):
    value = bpy.data.materials.new(name)
    value.diffuse_color = (*color, 1)
    value.use_nodes = True
    shader = value.node_tree.nodes.get("Principled BSDF")
    shader.inputs["Base Color"].default_value = (*color, 1)
    shader.inputs["Roughness"].default_value = roughness
    return value

black = material("Piumaggio carbone", (0.006, 0.009, 0.012))
wing = material("Ali ardesia", (0.012, 0.016, 0.020))
yellow = material("Becco e anello oculare", (0.75, 0.30, 0.015))
eye = material("Occhi", (0.005, 0.008, 0.01), 0.25)
feet = material("Zampe brune", (0.20, 0.10, 0.045))
objects = []

def group(name):
    value = bpy.data.objects.new(name, None)
    bpy.context.collection.objects.link(value)
    objects.append(value)
    return value

bird = group("Merlo")
head = group("Testa")
head.parent = bird

def oval(name, location, scale, mat, parent=bird, rotation=(0, 0, 0)):
    bpy.ops.mesh.primitive_uv_sphere_add(segments=16, ring_count=8, radius=1, location=location)
    value = bpy.context.object
    value.name = name
    value.scale = scale
    value.rotation_euler = rotation
    value.data.materials.append(mat)
    value.parent = parent
    objects.append(value)
    return value

def rod(name, start, end, radius, mat):
    from mathutils import Vector
    a, b = Vector(start), Vector(end)
    direction = b - a
    bpy.ops.mesh.primitive_cylinder_add(vertices=8, radius=radius, depth=direction.length, location=(a + b) / 2)
    value = bpy.context.object
    value.name = name
    value.rotation_euler = direction.to_track_quat("Z", "Y").to_euler()
    value.data.materials.append(mat)
    value.parent = bird
    objects.append(value)

oval("Corpo", (0, 0.017, 0.105), (0.042, 0.071, 0.052), black, rotation=(0.25, 0, 0))
oval("Petto", (0, -0.021, 0.123), (0.038, 0.047, 0.046), black)
oval("Testa", (0, -0.049, 0.173), (0.035, 0.039, 0.035), black, head)
oval("Coda", (0, 0.117, 0.074), (0.019, 0.069, 0.007), black, rotation=(-0.15, 0, 0))
for side in (-1, 1):
    oval("Ala " + str(side), (side * 0.037, 0.035, 0.113), (0.009, 0.059, 0.028), wing, rotation=(0.32, side * 0.20, 0))
    oval("Anello oculare " + str(side), (side * 0.032, -0.062, 0.183), (0.003, 0.007, 0.007), yellow, head)
    oval("Occhio " + str(side), (side * 0.0345, -0.0625, 0.183), (0.002, 0.0046, 0.0046), eye, head)
    ankle = (side * 0.017, 0.011, 0.011)
    rod("Gamba " + str(side), (side * 0.017, 0.018, 0.071), ankle, 0.003, feet)
    for toe in (-1, 0, 1):
        rod("Dito " + str(side) + ":" + str(toe), ankle, (side * 0.017 + toe * 0.01, -0.014, 0.004), 0.0018, feet)
    rod("Dito posteriore " + str(side), ankle, (side * 0.017, 0.025, 0.004), 0.0018, feet)

bpy.ops.mesh.primitive_cone_add(vertices=12, radius1=0.011, radius2=0.001, depth=0.033, location=(0, -0.099, 0.176), rotation=(math.pi / 2, 0, 0))
beak = bpy.context.object
beak.name = "Becco"
beak.data.materials.append(yellow)
beak.parent = head
objects.append(beak)

# Fix topology before export: do not leave quad/ngon tessellation to worker evaluation.
import bmesh
for value in objects:
    if value.type != "MESH":
        continue
    mesh = bmesh.new()
    mesh.from_mesh(value.data)
    bmesh.ops.triangulate(mesh, faces=list(mesh.faces), quad_method="FIXED", ngon_method="EAR_CLIP")
    mesh.to_mesh(value.data)
    mesh.free()
    for layer in list(value.data.uv_layers):
        value.data.uv_layers.remove(layer)
    value.data.update()

# Separate NLA tracks export as independently selectable clips; no authorship tools in the app.
for obj, name, field, frames in (
    (bird, "Respiro", "scale", [(1, (1, 1, 1)), (31, (1.015, 1.005, 1.015)), (61, (1, 1, 1))]),
    (head, "Guarda intorno", "rotation_euler", [(1, (0, 0, 0)), (31, (0, 0, 0.20)), (61, (0, 0, 0)), (91, (0, 0, -0.20)), (121, (0, 0, 0))]),
):
    for frame, values in frames:
        setattr(obj, field, values)
        obj.keyframe_insert(data_path=field, frame=frame)
    action = obj.animation_data.action
    action.name = name
    track = obj.animation_data.nla_tracks.new()
    track.name = name
    track.strips.new(name, 1, action)
    obj.animation_data.action = None

scene = bpy.context.scene
scene.render.fps = 30
scene.frame_end = 121
scene.frame_set(1)
scene.unit_settings.system = "METRIC"
bpy.ops.object.select_all(action="DESELECT")
for obj in objects:
    obj.select_set(True)
bpy.context.view_layer.objects.active = bird
sys.path.insert(0, str(folder))
from export_glb import write_glb
write_glb(delivery / "blackbird-v1.glb", objects)

# Studio preview is excluded from delivery geometry.
ground = material("Sfondo", (0.84, 0.88, 0.83))
bpy.ops.mesh.primitive_plane_add(size=200, location=(0, 0, 0))
bpy.context.object.data.materials.append(ground)
bpy.ops.object.camera_add(location=(0.44, -0.64, 0.31))
camera = bpy.context.object
from mathutils import Vector
camera.rotation_euler = (Vector((0, 0.015, 0.105)) - camera.location).to_track_quat("-Z", "Y").to_euler()
camera.data.type = "ORTHO"
camera.data.ortho_scale = 0.43
scene.camera = camera
for location, power, size in [((-0.3, -0.4, 0.7), 8, 0.5), ((0.4, 0.2, 0.5), 4, 0.4)]:
    bpy.ops.object.light_add(type="AREA", location=location)
    light = bpy.context.object
    light.data.energy = power
    light.data.shape = "DISK"
    light.data.size = size
    light.rotation_euler = (Vector((0, 0, 0.1)) - light.location).to_track_quat("-Z", "Y").to_euler()
scene.world.color = (0.3, 0.3, 0.3)
scene.render.engine = "CYCLES"
scene.cycles.samples = 32
scene.render.resolution_x = 640
scene.render.resolution_y = 640
scene.render.resolution_percentage = 100
scene.render.image_settings.file_format = "PNG"
scene.render.filepath = str(folder / "preview.png")
bpy.ops.wm.save_as_mainfile(filepath=str(folder / "blackbird-v1.blend"))
if "--skip-render" not in sys.argv:
    bpy.ops.render.render(write_still=True)
print("F14: source, GLB and preview generated")
