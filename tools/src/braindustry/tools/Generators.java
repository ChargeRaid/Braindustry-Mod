package braindustry.tools;

import arc.Core;
import arc.files.Fi;
import arc.func.Cons;
import arc.func.Func;
import arc.graphics.Color;
import arc.graphics.Pixmap;
import arc.graphics.Pixmaps;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.TextureRegion;
import arc.math.Mathf;
import arc.math.Rand;
import arc.math.geom.Vec2;
import arc.struct.IntIntMap;
import arc.struct.ObjectMap;
import arc.struct.ObjectSet;
import arc.struct.Seq;
import arc.util.ArcRuntimeException;
import arc.util.Log;
import arc.util.Tmp;
import arc.util.noise.Noise;
import arc.util.noise.Ridged;
import arc.util.noise.VoronoiNoise;
import braindustry.entities.abilities.ModAbility;
import braindustry.entities.abilities.OrbitalPlatformAbility;
import braindustry.entities.abilities.PowerGeneratorAbility;
import braindustry.gen.ModContentRegions;
import braindustry.type.ModUnitType;
import braindustry.type.SelfIconGenerator;
import mindustry.ctype.UnlockableContent;
import mindustry.game.Team;
import mindustry.gen.Legsc;
import mindustry.gen.Mechc;
import mindustry.gen.Unit;
import mindustry.graphics.BlockRenderer;
import mindustry.graphics.Pal;
import mindustry.type.Item;
import mindustry.type.StatusEffect;
import mindustry.type.Weapon;
import mindustry.world.Block;
import mindustry.world.blocks.ConstructBlock;
import mindustry.world.blocks.environment.*;
import mindustry.world.blocks.legacy.LegacyBlock;
import mindustry.world.meta.BuildVisibility;

import static braindustry.tools.ModImagePacker.*;
import static mindustry.Vars.*;

public class Generators {
    static final int logicIconSize = (int) iconMed, maxUiIcon = 128;
    //used for changing colors in the UI - testing only
    static final IntIntMap paletteMap = IntIntMap.with(
            //empty for now
            0x454545ff, 0x00000000,//0x32394bff,
            0x00000099, 0x00000000//0x000000ff
    );

    public static void run() {

        ObjectMap<Block, Pixmap> gens = new ObjectMap<>();
        Fi.get("../editor").mkdirs();
        if (false) {
            generate("cracks", () -> {
                for (int size = 1; size <= BlockRenderer.maxCrackSize; size++) {
                    int dim = size * 32;
                    int steps = BlockRenderer.crackRegions;
                    for (int i = 0; i < steps; i++) {
                        float fract = i / (float) steps;

                        Pixmap image = new Pixmap(dim, dim);
                        for (int x = 0; x < dim; x++) {
                            for (int y = 0; y < dim; y++) {
                                float dst = Mathf.dst((float) x / dim, (float) y / dim, 0.5f, 0.5f) * 2f;
                                if (dst < 1.2f && Ridged.noise2d(1, x, y, 3, 1f / 40f) - dst * (1f - fract) > 0.16f) {
                                    image.setRaw(x, y, Color.whiteRgba);
                                }
                            }
                        }

                        Pixmap output = new Pixmap(image.width, image.height);
                        int rad = 3;

                        //median filter
                        for (int x = 0; x < output.width; x++) {
                            for (int y = 0; y < output.height; y++) {
                                int whites = 0, clears = 0;
                                for (int cx = -rad; cx < rad; cx++) {
                                    for (int cy = -rad; cy < rad; cy++) {
                                        int wx = Mathf.clamp(cx + x, 0, output.width - 1), wy = Mathf.clamp(cy + y, 0, output.height - 1);
                                        int color = image.getRaw(wx, wy);
                                        if ((color & 0xff) > 127) {
                                            whites++;
                                        } else {
                                            clears++;
                                        }
                                    }
                                }
                                output.setRaw(x, y, whites >= clears ? Color.whiteRgba : Color.clearRgba);
                            }
                        }

                        Fi.get("cracks-" + size + "-" + i + ".png").writePng(output);
                    }
                }
            });
        }

        generate("block-icons", () -> {
            Pixmap colors = new Pixmap(content.blocks().size, 1);

            for (Block block : content.blocks()) {
                ModContentRegions.loadRegions(block);
                if (block.isAir() || block instanceof ConstructBlock || block instanceof OreBlock || block instanceof LegacyBlock)
                    continue;

                block.load();
                block.loadIcon();

                TextureRegion[] regions = block.getGeneratedIcons();

                if (block.variants > 0 || block instanceof Floor) {
                    for (TextureRegion region : block.variantRegions()) {
                        GenRegion gen = (GenRegion) region;
                        if (gen.path == null) continue;
                        gen.path.copyTo(Fi.get("../editor/editor-" + gen.path.name()));
                    }
                }

                for (TextureRegion region : block.makeIconRegions()) {
                    GenRegion gen = (GenRegion) region;
                    save(get(region).outline(block.outlineColor, block.outlineRadius), gen.name + "-outline");
                }

                Pixmap shardTeamTop = null;

                if (block.teamRegion.found()) {
                    Pixmap teamr = get(block.teamRegion);

                    for (Team team : Team.all) {
                        if (team.hasPalette) {
                            Pixmap out = new Pixmap(teamr.width, teamr.height);
                            teamr.each((x, y) -> {
                                int color = teamr.getRaw(x, y);
                                int index = color == 0xffffffff ? 0 : color == 0xdcc6c6ff ? 1 : color == 0x9d7f7fff ? 2 : -1;
                                out.setRaw(x, y, index == -1 ? teamr.getRaw(x, y) : team.palette[index].rgba());
                            });
                            save(out, block.name + "-team-" + team.name);

                            if (team == Team.sharded) {
                                shardTeamTop = out;
                            }
                        }
                    }
                }

                if (regions.length == 0) {
                    continue;
                }

                try {
                    Pixmap last = null;
                    if (block.outlineIcon) {
                        GenRegion region = (GenRegion) regions[block.outlinedIcon >= 0 ? block.outlinedIcon : regions.length - 1];
                        Pixmap base = get(region);
                        Pixmap out = last = base.outline(block.outlineColor, block.outlineRadius);

                        //do not run for legacy ones
                        if (block.outlinedIcon >= 0) {
                            //prevents the regions above from being ignored/invisible/etc
                            for (int i = block.outlinedIcon + 1; i < regions.length; i++) {
                                out.draw(get(regions[i]), true);
                            }
                        }


                        if (false) {
                            region.path.delete();
                            save(out, block.name);
                        }
                    }

                    if (!regions[0].found()) {
                        continue;
                    }
                    boolean selfGenerator = block instanceof SelfIconGenerator;
                    Pixmap image = get(regions[0]);

                    int i = 0;

                    for (TextureRegion region : regions) {
                        i++;
                        if (i == 1 && selfGenerator) {
                            image.draw(((SelfIconGenerator) block).generate(get(regions[0]), ModImagePacker::get));
                        } else if (i != regions.length || last == null) {
                            image.draw(get(region), true);
                        } else {
                            image.draw(last, true);
                        }

                        //draw shard (default team top) on top of first sprite
                        if (region == block.teamRegions[Team.sharded.id] && shardTeamTop != null) {
                            image.draw(shardTeamTop, true);
                        }
                    }
                    if (!(regions.length == 1 && regions[0] == Core.atlas.find(block.name) && shardTeamTop == null) || selfGenerator) {
                        save(image, /*"block-" +*/ block.name + "-full");
                    }

                    save(image, "../editor/" + block.name + "-icon-editor");

                    if (block.buildVisibility != BuildVisibility.hidden) {
                        saveScaled(image, block.name + "-icon-logic", logicIconSize);
                    }
                    saveScaled(image, "../ui/block-" + block.name + "-ui", Math.min(image.width, maxUiIcon));

                    boolean hasEmpty = false;
                    Color average = new Color(), c = new Color();
                    float asum = 0f;
                    for (int x = 0; x < image.width; x++) {
                        for (int y = 0; y < image.height; y++) {
                            Color color = c.set(image.get(x, y));
                            average.r += color.r * color.a;
                            average.g += color.g * color.a;
                            average.b += color.b * color.a;
                            asum += color.a;
                            if (color.a < 0.9f) {
                                hasEmpty = true;
                            }
                        }
                    }

                    average.mul(1f / asum);

                    if (block instanceof Floor) {
                        average.mul(0.77f);
                    } else {
                        average.mul(1.1f);
                    }
                    //encode square sprite in alpha channel
                    average.a = hasEmpty ? 0.1f : 1f;
                    colors.setRaw(block.id, 0, average.rgba());
                } catch (NullPointerException e) {
                    Log.err("Block &ly'@'&lr has an null region!", block);
                }
            }

            save(colors, "../../../assets/sprites/block_colors");
        });

        if (false) {
            generate("shallows", () -> {
                content.blocks().<ShallowLiquid>each(b -> b instanceof ShallowLiquid, floor -> {
                    Pixmap overlay = get(floor.liquidBase.region);
                    int index = 0;
                    for (TextureRegion region : floor.floorBase.variantRegions()) {
                        Pixmap res = get(region).copy();
                        for (int x = 0; x < res.width; x++) {
                            for (int y = 0; y < res.height; y++) {
                                res.set(x, y, Pixmap.blend((overlay.getRaw(x, y) & 0xffffff00) | (int) (floor.liquidOpacity * 255), res.getRaw(x, y)));
                            }
                        }

                        String name = floor.name + "" + (++index);
                        save(res, "../blocks/environment/" + name);
                        save(res, "../editor/editor-" + name);

                        gens.put(floor, res);
                    }
                });
            });
        }
        if (false) {
            generate("item-icons", () -> {
                for (UnlockableContent item : Seq.<UnlockableContent>withArrays(content.items(), content.liquids(), content.statusEffects())) {
                    if (item instanceof StatusEffect && !has(item.getContentType().name() + "-" + item.name)) {
                        continue;
                    }

                    Pixmap base = get(item.getContentType().name() + "-" + item.name);
                    //tint status effect icon color
                    if (item instanceof StatusEffect) {
                        StatusEffect stat = (StatusEffect) item;
                        Pixmap tint = base;
                        base.each((x, y) -> tint.setRaw(x, y, Color.muli(tint.getRaw(x, y), stat.color.rgba())));

                        //outline the image
                        Pixmap container = new Pixmap(38, 38);
                        container.draw(base, 3, 3, true);
                        base = container.outline(Pal.gray, 3);
                    }

                    saveScaled(base, item.name + "-icon-logic", logicIconSize);
                    save(base, "../ui/" + item.getContentType().name() + "-" + item.name + "-ui");
                }
            });
        }

        generate("unit-icons", () -> content.units().each(type -> {
            if (type.isHidden()) return; //hidden units don't generate

            ObjectSet<String> outlined = new ObjectSet<>();

            try {
                type.load();
                type.loadIcon();
                type.init();
                Func<Pixmap, Pixmap> outline = i -> {
                    int upScale = 0;
                    int x = 0, y = 0;
                    for (x = 0; x < i.width; x++) {
                        for (y = 0; y < 3; y++) {
                            boolean bool = i.getA(x, y) == 0 && i.getA(x, i.height - y - 1) == 0;
                            if (!bool) {
                                upScale = Math.max(y, upScale);
                            }
                        }
                    }
                    for (y = 0; y < i.height; y++) {
                        for (x = 0; x < 3; x++) {
                            boolean bool = i.getA(x, y) == 0 && i.getA(i.width - x - 1, y) == 0;
                            if (!bool) {
                                upScale = Math.max(x, upScale);
                            }
                        }
                    }
                    if (upScale != 0) {
                        Pixmap pixmap = new Pixmap(i.width + upScale * 2, i.height + upScale * 2);
                        pixmap.draw(i, pixmap.width / 2 - i.width / 2, pixmap.height / 2 - i.height / 2);
                        i = pixmap;
                    }
                    return i.outline(Pal.darkerMetal, 3);
                };
                Cons<TextureRegion> outliner = t -> {
                    if (t != null && t.found()) {
                        replace(t, outline.get(get(t)));
                    }
                };


                Seq<Weapon> abilitiesWeapons = new Seq<>();
                Seq<TextureRegion> outlineRegions = new Seq<>();
                Seq<ModAbility> modAbilities = new Seq<>();
                if (type instanceof ModUnitType) {
                    modAbilities.addAll(((ModUnitType) type).getModAbilities());
                    modAbilities.each(modAbility -> {
                        modAbility.load();
                        abilitiesWeapons.addAll(modAbility.weapons());
                        outlineRegions.addAll(modAbility.outlineRegions());
                    });
                }
                for (Weapon weapon : abilitiesWeapons) {
                    if (outlined.add(weapon.name) && has(weapon.name)) {
                        save(outline.get(get(weapon.name)), weapon.name + "-outline");
                    }
                }
                /*for (Weapon weapon : type.weapons.copy().addAll(abilitiesWeapons)) {
                    if (outlined.add(weapon.name) && has(weapon.name)) {
                        save(outline.get(get(weapon.name)), weapon.name + "-outline");
                    }
                }*/
                for (TextureRegion outlineRegion : outlineRegions) {
                    if (!outlineRegion.found()) continue;
                    save(outline.get(get(outlineRegion)), outlineRegion.asAtlas().name + "-outline");
                }
                outliner.get(type.jointRegion);
                outliner.get(type.footRegion);
                outliner.get(type.legBaseRegion);
                outliner.get(type.baseJointRegion);
                Unit inst = type.constructor.get();
                if (inst instanceof Legsc) outliner.get(type.legRegion);

                Pixmap image = outline.get(get(type.region));


//                save(image, type.name + "-outline");

                for (ModAbility modAbility : modAbilities) {
                    if (modAbility instanceof PowerGeneratorAbility) {
                        PowerGeneratorAbility ability = (PowerGeneratorAbility) modAbility;
                        if (!ability.bottomRegion.found()) continue;
                        Pixmap bottom = get(ability.bottomRegion);
                        bottom = drawScaleAt(bottom, image, bottom.width / 2 - image.width / 2, bottom.height / 2 - image.height / 2);
                        image = drawScaleAt(image, bottom, image.width / 2 - bottom.width / 2, image.height / 2 - bottom.height / 2);
                    }
                }
                //draw mech parts
                if (inst instanceof Mechc) {
                    drawCenter(image, get(type.baseRegion));
                    drawCenter(image, get(type.legRegion));
                    drawCenter(image, get(type.legRegion).flipX());
                    drawCenter(image, get(type.region));
//                    image.draw(get(type.region), true);
                }

                //draw outlines
                for (Weapon weapon : type.weapons) {
                    weapon.load();

                    Pixmap pixmap = weapon.flipSprite ? outline.get(get(weapon.region)).flipX() : outline.get(get(weapon.region));
                    int x = (int) (weapon.x / Draw.scl + image.width / 2f - weapon.region.width / 2f);
                    int y = (int) (-weapon.y / Draw.scl + image.height / 2f - weapon.region.height / 2f);
                    image = drawScaleAt(image, pixmap, x, y);
                }

                //draw base region on top to mask weapons
                drawCenter(image, get(type.region));
//                image.draw(get(type.region), true);
                int baseColor = Color.valueOf("ffa665").rgba();

                Pixmap baseCell = get(type.cellRegion);
                Pixmap cell = new Pixmap(type.cellRegion.width, type.cellRegion.height);
                cell.each((x, y) -> cell.set(x, y, Color.muli(baseCell.getRaw(x, y), baseColor)));
//                image.draw(cell, image.width / 2 - cell.width / 2, image.height / 2 - cell.height / 2, image.width / 2 - cell.width / 2, image.height / 2 - cell.height / 2, true);
                drawCenter(image, cell);
                for (Weapon weapon : type.weapons) {
                    weapon.load();

                    Pixmap wepReg = weapon.top ? outline.get(get(weapon.region)) : get(weapon.region);
                    if (weapon.flipSprite) {
                        wepReg = wepReg.flipX();
                    }

                    image = drawScaleAt(image, wepReg, (int) (weapon.x / Draw.scl + image.width / 2f - weapon.region.width / 2f), (int) (-weapon.y / Draw.scl + image.height / 2f - weapon.region.height / 2f));
                }


                image = clearAlpha(image);
                save(image, /*"unit-" +*/ type.name + "-shadow");
                for (ModAbility modAbility : modAbilities) {
                    if (modAbility instanceof OrbitalPlatformAbility) {
                        OrbitalPlatformAbility ability = (OrbitalPlatformAbility) modAbility;
                        if (!ability.region().found()) continue;
                        Pixmap region = outline.get(get(ability.region()));
                        Log.info("type: @", type);
                        for (int i = 0; i < ability.weapons.length; i++) {
                            int dx = 0, dy = 0;
                            Tmp.v1.trns(i / (float) ability.platformsCount() * 360f, type.hitSize, type.hitSize).scl(1f / Draw.scl);
                            dx = (int) Tmp.v1.x;
                            dy = (int) Tmp.v1.y;
                            image = drawScaleAt(image, region, image.width / 2 - region.width / 2 + dx, image.height / 2 - region.height / 2 + dy);

                            Weapon weapon = ability.weapons[i];
                            if (weapon == null) continue;
                            weapon.load();
                            if (!weapon.region.found()) {
                                Log.err("cannot find region @", weapon.region);
                            }
                            Pixmap pixmap = get(weapon.region);
                            image = drawScaleAt(image, outline.get(pixmap), image.width / 2 - pixmap.width / 2 + dx, image.height / 2 - pixmap.height / 2 + dy);

                        }
//                        Pixmap region = get(ability.region());
//                        drawScaleAt(image,bottom,image.width/2-bottom.width/2,image.height/2-bottom.height/2);
                    }
                }
                image = clearAlpha(image);
                save(image, /*"unit-" +*/ type.name + "-full");
                Rand rand = new Rand();
                rand.setSeed(type.name.hashCode());

                //generate random wrecks

                int splits = 3;
                float degrees = rand.random(360f);
                float offsetRange = Math.max(image.width, image.height) * 0.15f;
                Vec2 offset = new Vec2(1, 1).rotate(rand.random(360f)).setLength(rand.random(0, offsetRange)).add(image.width / 2f, image.height / 2f);

                Pixmap[] wrecks = new Pixmap[splits];
                for (int i = 0; i < wrecks.length; i++) {
                    wrecks[i] = new Pixmap(image.width, image.height);
                }

                VoronoiNoise vn = new VoronoiNoise(type.id, true);
                Pixmap imageCache = image;
                image.each((x, y) -> {
                    //add darker cracks on top
                    boolean rValue = Math.max(Ridged.noise2d(1, x, y, 3, 1f / (20f + imageCache.width / 8f)), 0) > 0.16f;
                    //cut out random chunks with voronoi
                    boolean vval = vn.noise(x, y, 1f / (14f + imageCache.width / 40f)) > 0.47;

                    float dst = offset.dst(x, y);
                    //distort edges with random noise
                    float noise = (float) Noise.rawNoise(dst / (9f + imageCache.width / 70f)) * (60 + imageCache.width / 30f);
                    int section = (int) Mathf.clamp(Mathf.mod(offset.angleTo(x, y) + noise + degrees, 360f) / 360f * splits, 0, splits - 1);
                    if (!vval) wrecks[section].setRaw(x, y, Color.muli(imageCache.getRaw(x, y), rValue ? 0.7f : 1f));
                });

                for (int i = 0; i < wrecks.length; i++) {
                    save(wrecks[i], "../rubble/" + type.name + "-wreck" + i);
                }

                int maxd = Math.min(Math.max(image.width, image.height), maxUiIcon);
                Pixmap fit = new Pixmap(maxd, maxd);
                drawScaledFit(fit, image);

                saveScaled(fit, type.name + "-icon-logic", logicIconSize);
                save(fit, "../ui/" + type.name + "-ui");
            } catch (Exception e) {
                Log.err("WARNING: Skipping unit " + type.name + ": @", e);
            }

        }));

        generate("ore-icons", () -> {
            content.blocks().<OreBlock>each(b -> b instanceof OreBlock, ore -> {
                String prefix = ore instanceof WallOreBlock ? "wall-ore-" : "ore-";
                Item item = ore.itemDrop;
                int shadowColor = Color.rgba8888(0, 0, 0, 0.3f);

                for (int i = 0; i < ore.variants; i++) {
                    //get base image to draw on
                    Pixmap base = get((ore instanceof WallOreBlock ? "wall-" : "") + item.name + (i + 1));
                    Pixmap image = base.copy();

                    int offset = image.width / tilesize - 1;

                    for (int x = 0; x < image.width; x++) {
                        for (int y = offset; y < image.height; y++) {
                            //draw semi transparent background
                            if (base.getA(x, y - offset) != 0) {
                                image.setRaw(x, y, Pixmap.blend(shadowColor, base.getRaw(x, y)));
                            }
                        }
                    }

                    image.draw(base, true);
                    save(image, "../blocks/environment/" + prefix + item.name + (i + 1));
                    save(image, "../editor/editor-" + prefix + item.name + (i + 1));

                    save(image, /*"block-" +*/ ore.name + "-full");
                    save(image, "../ui/block-" + ore.name + "-ui");
                }
            });
        });

        generate("edges", () -> {
            content.blocks().<Floor>each(b -> b instanceof Floor && !(b instanceof OverlayFloor), floor -> {

                if (has(floor.name + "-edge") || floor.blendGroup != floor) {
                    return;
                }

                try {
                    Pixmap image = gens.get(floor, get(floor.getGeneratedIcons()[0]));
                    Pixmap edge = get("edge-stencil");
                    Pixmap result = new Pixmap(edge.width, edge.height);

                    for (int x = 0; x < edge.width; x++) {
                        for (int y = 0; y < edge.height; y++) {
                            result.set(x, y, Color.muli(edge.getRaw(x, y), image.get(x % image.width, y % image.height)));
                        }
                    }

                    save(result, "../blocks/environment/" + floor.name + "-edge");

                } catch (Exception ignored) {
                }
            });
        });

        if (false) {
            generate("scorches", () -> {
                for (int size = 0; size < 10; size++) {
                    for (int i = 0; i < 3; i++) {
                        mindustry.tools.Generators.ScorchGenerator gen = new mindustry.tools.Generators.ScorchGenerator();
                        double multiplier = 30;
                        double ss = size * multiplier / 20.0;

                        gen.seed = Mathf.random(100000);
                        gen.size += size * multiplier;
                        gen.scale = gen.size / 80f * 18f;
                        //gen.nscl -= size * 0.2f;
                        gen.octaves += ss / 3.0;
                        gen.pers += ss / 10.0 / 5.0;

                        gen.scale += Mathf.range(3f);
                        gen.scale -= ss * 2f;
                        gen.nscl -= Mathf.random(1f);

                        Pixmap out = gen.generate();
                        Pixmap median = Pixmaps.median(out, 2, 0.75);
                        Fi.get("../rubble/scorch-" + size + "-" + i + ".png").writePng(median);
                        out.dispose();
                        median.dispose();
                    }
                }
            });
        }
    }

    private static Pixmap clearAlpha(Pixmap image) {
        int x = 0, y = 0, topx = image.width, topy = image.height;
        //check x-
        for (int dx = 0; dx < image.width; dx++) {
            for (int dy = 0; dy < image.height; dy++) {
                if (image.getA(dx, dy) != 0) {
                    dx = topx;
                    break;
                }
                x = dx;
            }
        }
        //check y-
        for (int dy = 0; dy < image.height; dy++) {
            for (int dx = 0; dx < image.width; dx++) {
                if (image.getA(dx, dy) != 0) {
                    dy = topy;
                    break;
                }
                y = dy;
            }
        }
        //check x+
        for (int dx = image.width - 1; dx > -1; dx--) {
            for (int dy = image.height - 1; dy > -1; dy--) {
                if (image.getA(dx, dy) != 0) {
                    dx = -1;
                    break;
                }
                topx = dx;

            }
        }
        //check y+
        for (int dy = image.height - 1; dy > -1; dy--) {
            for (int dx = image.width - 1; dx > -1; dx--) {
                if (image.getA(dx, dy) != 0) {
                    dy = -1;
                    break;
                }
                topy = dy+1;

            }
        }
        if (x != 0 || y != 0 || topx != image.width || topy != image.height) {
            int width = Math.min(x, image.width - topx);
            int height = Math.min(y,image.height-topy);
            Pixmap pixmap = new Pixmap(image.width - width*2, image.height - height*2);
//            pixmap.draw(image, 0, 0, x, y, topx, topy);
            drawCenter(pixmap,image);
            return pixmap;
        }
        return image;
    }

    private static Pixmap drawScaleAt(Pixmap image, Pixmap other, int destx, int desty) {
        int widthScale = 0, heightScale = 0;
        if (destx > image.width) {
            widthScale = destx - image.width + other.width;
        } else if (destx + other.width < 0) {
            widthScale = -(destx);
        } else if (destx + other.width > image.width || destx < 0) {
            int dif = destx + other.width - image.width;
            int dx;
            for (int y = 0; y < other.height; y++) {
                for (dx = 0; dx < dif; dx++) {
                    if (other.getA(other.width - dx - 1, y) == 0) continue;
                    widthScale = Math.max(widthScale, dx);
                }
                for (dx = 0; dx < -destx; dx++) {
                    if (other.getA(dx, y) == 0) continue;
                    widthScale = Math.max(widthScale, dx);
                }
            }
        }

        if (image.height < desty) {
            heightScale = desty - image.height + other.height;
        } else if (desty + other.height < 0) {
            heightScale = -(desty + other.height);
        } else if (desty + other.height > image.height || desty < 0) {
            int dif = desty + other.height - image.height;
            int dy;
            for (int x = 0; x < other.width; x++) {
                for (dy = 0; dy < dif; dy++) {
                    if (other.getA(x, other.height - dy - 1) == 0) continue;
                    heightScale = Math.max(heightScale, dy);
                }
                for (dy = 0; dy < -destx; dy++) {
                    if (other.getA(x, dy) == 0) continue;
                    heightScale = Math.max(heightScale, dy);
                }
            }
        }
        if (widthScale != 0 || heightScale != 0) {
            Pixmap pixmap;

            try {
                pixmap = new Pixmap(widthScale * 2 + image.width, image.height + heightScale * 2);
            } catch (ArcRuntimeException arcRuntimeException) {
                Log.err(arcRuntimeException);
                return image;
            }
            drawCenter(pixmap, image);
            pixmap.draw(other, destx + widthScale, desty + heightScale, true);
            return pixmap;
        }
        image.draw(other,
                destx,
                desty,
                true
        );
        return image;
    }
}
