// priority: -20
const NUMISMATICS = Java.loadClass("dev.ithundxr.createnumismatics.Numismatics");
global.GLOBAL_BANK = NUMISMATICS.BANK;

global.showPonderLayer = (scene, speed, height, exclude) => {
  for (let x = 0; x <= 5; x++) {
    for (let z = 0; z <= 5; z++) {
      if (!exclude || !(x == exclude.x && z == exclude.z))
        scene.world.showSection([x, height, z], Facing.DOWN);
    }
    if (speed > 0) scene.idle(speed);
  }
};

global.coinMap = [
  { coin: "numismatics:prismatic_coin", value: 16777216 },
  { coin: "numismatics:ancient_coin", value: 262144 },
  { coin: "numismatics:neptunium_coin", value: 32768 },
  { coin: "numismatics:sun", value: 4096 },
  { coin: "numismatics:crown", value: 512 },
  { coin: "numismatics:cog", value: 64 },
  { coin: "numismatics:sprocket", value: 16 },
  { coin: "numismatics:bevel", value: 8 },
  { coin: "numismatics:spur", value: 1 },
];

global.formatPrice = (number) => {
  return number.toString().replace(/\B(?=(\d{3})+(?!\d))/g, ",");
};

global.formatName = (name) => {
  if (name.length === 0) return "";
  return name.charAt(0).toUpperCase() + name.slice(1);
};

// For cases where prices are auto-generated, round
const roundPrice = (price) => {
  for (let i = 0; i < global.coinMap.length; i++) {
    let { value } = global.coinMap[i];

    if (price % value === 0) {
      for (let k = 0; k < global.coinMap.length; k++) {
        if (price / global.coinMap[i - k].value <= 64) {
          return global.coinMap[i - k].value * Math.round(price / global.coinMap[i - k].value);
        }
      }
    }
  }
  return price;
};
// Pristine Crystalarium items
global.pristine = [];

// Ores
global.ore = [
  { item: "minecraft:raw_copper", value: 1 },
  { item: "minecraft:raw_copper_block", value: 4 },
  { item: "minecraft:raw_iron", value: 1 },
  { item: "minecraft:raw_iron_block", value: 7 },
  { item: "minecraft:coal", value: 1 },
  { item: "minecraft:coal_block", value: 7 },
  { item: "minecraft:raw_gold", value: 2 },
  { item: "minecraft:raw_gold_block", value: 14 },
  { item: "create:raw_zinc", value: 1 },
  { item: "create:raw_zinc_block", value: 7 },
  { item: "minecraft:redstone", value: 1 },
  { item: "minecraft:redstone_block", value: 7 },
  { item: "minecraft:lapis_lazuli", value: 1 },
  { item: "minecraft:lapis_block", value: 6 },
  { item: "minecraft:emerald", value: 3 },
  { item: "minecraft:emerald_block", value: 29 },
  { item: "minecraft:amethyst_shard", value: 1 },
  { item: "minecraft:amethyst_block", value: 3 },
  { item: "minecraft:quartz", value: 1 },
  { item: "minecraft:quartz_block", value: 3 },
  { item: "etcetera:raw_bismuth", value: 3 },
  { item: "etcetera:raw_bismuth_block", value: 29 },
  { item: "oreganized:raw_lead", value: 5 },
  { item: "oreganized:raw_lead_block", value: 43 },
  { item: "society:sparkstone", value: 6 },
  { item: "society:sparkstone_block", value: 58 },
  { item: "oreganized:raw_silver", value: 6 },
  { item: "oreganized:raw_silver_block", value: 58 },
  { item: "minecraft:diamond", value: 26 },
  { item: "minecraft:diamond_block", value: 230 },
  { item: "minecraft:netherite_scrap", value: 102 },
  { item: "aquaculture:neptunium_ingot", value: 256 },
  { item: "aquaculture:neptunium_block", value: 2304 },
];
[
  { item: "minecraft:emerald", value: 3 },
  { item: "minecraft:diamond", value: 26 },
  { item: "minecraft:lapis_lazuli", value: 1 },
  { item: "minecraft:quartz", value: 1 },
  { item: "minecraft:amethyst_shard", value: 1 },
  { item: "minecraft:prismarine_crystals", value: 20 },
].forEach((mineral) => {
  global.pristine.push({
    item: `society:pristine_${mineral.item.path}`,
    value: mineral.value * 4,
  });
});

// Geodes
// Geode
global.geodeList = [
  { item: "society:allanite", value: 13 },
  { item: "society:calcite_gem", value: 6 },
  { item: "society:celestine", value: 11 },
  { item: "society:froggy_helm", value: 26 },
  { item: "society:earth_crystal", value: 5 },
  { item: "society:granite_slate", value: 26 },
  { item: "society:jagoite", value: 27 },
  { item: "society:jamborite", value: 13 },
  { item: "society:limestone_pebble", value: 2 },
  { item: "society:malachite", value: 26 },
  { item: "society:mudstone", value: 2 },
  { item: "society:nekoite", value: 8 },
  { item: "society:orpiment", value: 8 },
  { item: "society:petrified_slime", value: 13 },
  { item: "society:sandstone_slate", value: 6 },
  { item: "society:slate", value: 8 },
  { item: "society:thunder_egg", value: 26 },
];
global.geodeList.forEach((mineral) => {
  if (mineral.item !== "society:froggy_helm")
    global.pristine.push({
      item: `society:pristine_${mineral.item.path}`,
      value: mineral.value * 6,
    });
});

// Frozen Geode
global.frozenGeodeList = [
  { item: "society:aerinite", value: 13 },
  { item: "society:ribbit_drum", value: 10 },
  { item: "society:esperite", value: 10 },
  { item: "society:fairy_stone", value: 26 },
  { item: "society:fluorapatite", value: 20 },
  { item: "society:geminite", value: 12 },
  { item: "society:ghost_crystal", value: 20 },
  { item: "society:hematite", value: 12 },
  { item: "society:kyanite", value: 26 },
  { item: "society:lunarite", value: 20 },
  { item: "society:marble", value: 11 },
  { item: "society:ocean_stone", value: 22 },
  { item: "society:opal", value: 12 },
  { item: "society:pyrite", value: 13 },
  { item: "society:soapstone", value: 13 },
  { item: "society:frozen_tear", value: 6 },
];
global.frozenGeodeList.forEach((mineral) => {
  if (mineral.item !== "society:ribbit_drum")
    global.pristine.push({
      item: `society:pristine_${mineral.item.path}`,
      value: mineral.value * 6,
    });
});

// Magma Geode
global.magmaGeodeList = [
  { item: "society:baryte", value: 5 },
  { item: "society:basalt_shard", value: 18 },
  { item: "society:bixbyite", value: 30 },
  { item: "society:dolomite", value: 30 },
  { item: "society:ribbit_gadget", value: 19 },
  { item: "society:fire_opal", value: 35 },
  { item: "society:fire_quartz", value: 10 },
  { item: "society:helvite", value: 51 },
  { item: "society:jasper", value: 14 },
  { item: "society:lemon_stone", value: 19 },
  { item: "society:neptunite", value: 40 },
  { item: "society:pure_obsidian", value: 19 },
  { item: "society:star_shards", value: 51 },
  { item: "society:tigerseye", value: 27 },
];
global.magmaGeodeList.forEach((mineral) => {
  if (mineral.item !== "society:ribbit_gadget")
    global.pristine.push({
      item: `society:pristine_${mineral.item.path}`,
      value: mineral.value * 6,
    });
});

global.gems = [
  { item: "society:aquamarine", value: 18 },
  { item: "society:ruby", value: 26 },
  { item: "society:amethyst_chunk", value: 11 },
  { item: "society:topaz", value: 19 },
  { item: "society:jade", value: 51 },
];
global.gems.forEach((mineral) => {
  global.pristine.push({
    item: `society:pristine_${mineral.item.path}`,
    value: mineral.value * 6,
  });
});

// Mining misc
global.miscGeologist = [
  { item: "society:geode", value: 2 },
  { item: "society:frozen_geode", value: 3 },
  { item: "society:magma_geode", value: 5 },
  { item: "society:omni_geode", value: 13 },
  { item: "society:prismatic_shard", value: 205 },
  { item: "quark:diamond_heart", value: 75 },
  { item: "quark:red_corundum", value: 3 },
  { item: "quark:red_corundum_cluster", value: 2 },
  { item: "quark:orange_corundum", value: 3 },
  { item: "quark:orange_corundum_cluster", value: 2 },
  { item: "quark:yellow_corundum", value: 3 },
  { item: "quark:yellow_corundum_cluster", value: 2 },
  { item: "quark:green_corundum", value: 3 },
  { item: "quark:green_corundum_cluster", value: 2 },
  { item: "quark:blue_corundum", value: 3 },
  { item: "quark:blue_corundum_cluster", value: 2 },
  { item: "quark:indigo_corundum", value: 3 },
  { item: "quark:indigo_corundum_cluster", value: 2 },
  { item: "quark:violet_corundum", value: 3 },
  { item: "quark:violet_corundum_cluster", value: 2 },
  { item: "quark:white_corundum", value: 3 },
  { item: "quark:white_corundum_cluster", value: 2 },
  { item: "quark:black_corundum", value: 3 },
  { item: "quark:black_corundum_cluster", value: 2 },
  { item: "minecraft:golden_apple", value: 13 },
];

// Artifacts
global.artifacts = [
  { item: "society:legendary_ink", value: 14 },
  { item: "society:holy_symbol", value: 16 },
  { item: "society:ember_crystal_cluster", value: 18 },
  { item: "society:living_flesh", value: 19 },
  { item: "society:source_gem", value: 21 },
  { item: "society:glitched_vhs", value: 26 },
  { item: "society:spider_silk", value: 32 },
  { item: "society:toy_train", value: 43 },
  { item: "society:aquamagical_dust", value: 51 },
  { item: "society:wheel_of_adaptation", value: 58 },
  { item: "society:perfect_cherry", value: 78 },
  { item: "society:mini_oni_eye", value: 70 },
  { item: "society:production_science_pack", value: 102 },
  { item: "society:steamy_gadget", value: 65 },
  { item: "society:amulet_of_light", value: 128 },
  { item: "society:beemonican_seal", value: 256 },
  { item: "society:princess_hairbrush", value: 358 },
  { item: "society:heart_of_neptunium", value: 410 },
  { item: "society:token_of_unity", value: 1 },
];

// Relics
global.relics = [
  { item: "relics:relic_experience_bottle", value: 8 },
  { item: "relics:horse_flute", value: 29 },
  { item: "relics:hunter_belt", value: 29 },
  { item: "relics:ice_skates", value: 30 },
  { item: "relics:spatial_sign", value: 30 },
  { item: "relics:wool_mitten", value: 30 },
  { item: "relics:magma_walker", value: 102 },
  { item: "relics:bastion_ring", value: 102 },
  { item: "relics:reflection_necklace", value: 32 },
  { item: "relics:jellyfish_necklace", value: 32 },
  { item: "relics:ice_breaker", value: 32 },
  { item: "relics:amphibian_boot", value: 32 },
  { item: "relics:aqua_walker", value: 32 },
  { item: "relics:roller_skates", value: 51 },
  { item: "relics:leather_belt", value: 51 },
  { item: "relics:drowned_belt", value: 102 },
  { item: "relics:shadow_glaive", value: 102 },
  { item: "relics:elytra_booster", value: 154 },
  { item: "relics:enders_hand", value: 154 },
  { item: "relics:holy_locket", value: 410 },
  { item: "relics:arrow_quiver", value: 410 },
  { item: "relics:chorus_inhibitor", value: 307 },
  { item: "relics:midnight_robe", value: 307 },
  { item: "relics:infinity_ham", value: 819 },
  { item: "relics:space_dissector", value: 1638 },
  { item: "relics:spore_sack", value: 256 },
  { item: "relics:rage_glove", value: 256 },
];

// Crops
/*
  Farmland crop calc: (6 * stages + (2 * (4 - fertile seasons))) / count - (if reseedable, - 8)
  Master Cult: * 1.5
*/
global.crops = [
  { item: "vinery:white_grape", value: 2 },
  { item: "vinery:white_grape_bag", value: 18 },
  { item: "vinery:red_grape", value: 2 },
  { item: "vinery:red_grape_bag", value: 18 },
  { item: "minecraft:sweet_berries", value: 1 },
  { item: "quark:berry_sack", value: 4 },
  { item: "windswept:wild_berries", value: 1 },
  { item: "windswept:wild_berry_basket", value: 7 },
  { item: "vinery:savanna_grapes_white", value: 2 },
  { item: "vinery:taiga_grapes_red", value: 2 },
  { item: "vinery:taiga_grapes_white", value: 2 },
  { item: "vinery:jungle_grapes_white", value: 2 },
  { item: "vinery:jungle_grapes_red", value: 2 },
  { item: "vinery:savanna_grapes_red", value: 2 },
  { item: "nethervinery:crimson_grape", value: 2 },
  { item: "nethervinery:crimson_grape_crate", value: 22 },
  { item: "nethervinery:warped_grape", value: 2 },
  { item: "nethervinery:warped_grape_crate", value: 22 },
  { item: "autumnity:foul_berries", value: 1 },
  { item: "herbalbrews:rooibos_leaf", value: 1 },
  { item: "herbalbrews:rooibos_leaf_block", value: 7 },
  { item: "herbalbrews:green_tea_leaf", value: 2 },
  { item: "herbalbrews:green_tea_leaf_block", value: 18 },
  { item: "herbalbrews:yerba_mate_leaf", value: 1 },
  { item: "herbalbrews:yerba_mate_leaf_block", value: 5 },
  { item: "herbalbrews:coffee_beans", value: 1 },
  { item: "herbalbrews:ground_coffee", value: 1 },
  { item: "herbalbrews:coffee_beans_sack", value: 7 },
  { item: "farm_and_charm:barley", value: 1 },
  { item: "farm_and_charm:barley_ball", value: 10 },
  { item: "minecraft:melon_slice", value: 1 },
  { item: "minecraft:melon", value: 8 },
  { item: "minecraft:cocoa_beans", value: 1 },
  { item: "quark:cocoa_beans_sack", value: 4 },
  { item: "farm_and_charm:onion", value: 1 },
  { item: "farm_and_charm:onion_bag", value: 11 },
  { item: "farmersdelight:tomato", value: 2 },
  { item: "farmersdelight:rotten_tomato", value: 1 },
  { item: "farmersdelight:tomato_crate", value: 22 },
  { item: "minecraft:nether_wart", value: 1 },
  { item: "quark:nether_wart_sack", value: 5 },
  { item: "minecraft:carrot", value: 2 },
  { item: "minecraft:golden_carrot", value: 15 },
  { item: "quark:golden_carrot_crate", value: 135 },
  { item: "farm_and_charm:carrot_bag", value: 21 },
  { item: "farm_and_charm:corn", value: 3 },
  { item: "farm_and_charm:corn_bag", value: 25 },
  { item: "atmospheric:yucca_fruit", value: 1 },
  { item: "atmospheric:yucca_bundle", value: 7 },
  { item: "atmospheric:yucca_cask", value: 7 },
  { item: "atmospheric:currant", value: 1 },
  { item: "atmospheric:currant_crate", value: 7 },
  { item: "vintagedelight:peanut", value: 2 },
  { item: "vintagedelight:peanut_crate", value: 22 },
  { item: "vintagedelight:gearo_berry", value: 2 },
  { item: "vintagedelight:gearo_berry_bag", value: 22 },
  { item: "minecraft:potato", value: 2 },
  { item: "farm_and_charm:potato_bag", value: 22 },
  { item: "minecraft:poisonous_potato", value: 1 },
  { item: "society:eggplant", value: 4 },
  { item: "society:eggplant_crate", value: 38 },
  { item: "veggiesdelight:turnip", value: 4 },
  { item: "veggiesdelight:turnip_crate", value: 32 },
  { item: "veggiesdelight:broccoli", value: 5 },
  { item: "veggiesdelight:broccoli_crate", value: 49 },
  { item: "veggiesdelight:zucchini", value: 7 },
  { item: "veggiesdelight:zucchini_crate", value: 65 },
  { item: "veggiesdelight:cauliflower", value: 5 },
  { item: "veggiesdelight:cauliflower_crate", value: 43 },
  { item: "veggiesdelight:garlic", value: 3 },
  { item: "veggiesdelight:garlic_crate", value: 24 },
  { item: "veggiesdelight:sweet_potato", value: 2 },
  { item: "veggiesdelight:sweet_potato_crate", value: 18 },
  { item: "veggiesdelight:bellpepper", value: 3 },
  { item: "veggiesdelight:bellpepper_crate", value: 29 },
  { item: "farm_and_charm:oat", value: 4 },
  { item: "farm_and_charm:oat_ball", value: 32 },
  { item: "snowyspirit:ginger", value: 2 },
  { item: "snowyspirit:ginger_crate", value: 22 },
  { item: "farm_and_charm:lettuce", value: 2 },
  { item: "farm_and_charm:lettuce_bag", value: 22 },
  { item: "minecraft:beetroot", value: 2 },
  { item: "farm_and_charm:beetroot_bag", value: 22 },
  { item: "beachparty:coconut", value: 1 },
  { item: "minecraft:apple", value: 1 },
  { item: "vinery:apple_bag", value: 7 },
  { item: "botania:white_mushroom", value: 1 },
  { item: "botania:orange_mushroom", value: 1 },
  { item: "botania:magenta_mushroom", value: 1 },
  { item: "botania:light_blue_mushroom", value: 1 },
  { item: "botania:yellow_mushroom", value: 1 },
  { item: "botania:lime_mushroom", value: 1 },
  { item: "botania:pink_mushroom", value: 1 },
  { item: "botania:black_mushroom", value: 1 },
  { item: "botania:red_mushroom", value: 1 },
  { item: "botania:green_mushroom", value: 1 },
  { item: "botania:brown_mushroom", value: 1 },
  { item: "botania:blue_mushroom", value: 1 },
  { item: "botania:purple_mushroom", value: 1 },
  { item: "botania:cyan_mushroom", value: 1 },
  { item: "botania:light_gray_mushroom", value: 1 },
  { item: "botania:gray_mushroom", value: 1 },
  { item: "minecraft:red_mushroom", value: 1 },
  { item: "farmersdelight:red_mushroom_colony", value: 5 },
  { item: "minecraft:brown_mushroom", value: 1 },
  { item: "farmersdelight:brown_mushroom_colony", value: 5 },
  { item: "minecraft:crimson_fungus", value: 2 },
  { item: "mynethersdelight:crimson_fungus_colony", value: 10 },
  { item: "minecraft:warped_fungus", value: 2 },
  { item: "mynethersdelight:warped_fungus_colony", value: 10 },
  { item: "verdantvibes:bracket_mushroom", value: 3 },
  { item: "species:alphacene_mushroom", value: 3 },
  { item: "minecraft:bamboo_block", value: 1 },
  { item: "society:sturdy_bamboo_block", value: 8 },
  { item: "twigs:bamboo_thatch", value: 1 },
  { item: "minecraft:cactus", value: 1 },
  { item: "quark:cactus_block", value: 11 },
  { item: "moreminecarts:glass_spines", value: 2 },
  { item: "vinery:cherry", value: 1 },
  { item: "vinery:rotten_cherry", value: 1 },
  { item: "vinery:cherry_bag", value: 11 },
  { item: "farm_and_charm:strawberry", value: 2 },
  { item: "farm_and_charm:strawberry_bag", value: 16 },
  { item: "society:salmonberry", value: 2 },
  { item: "society:salmonberry_crate", value: 18 },
  { item: "society:boysenberry", value: 2 },
  { item: "society:boysenberry_crate", value: 14 },
  { item: "society:cranberry", value: 2 },
  { item: "society:cranberry_crate", value: 16 },
  { item: "society:crystalberry", value: 2 },
  { item: "society:crystalberry_crate", value: 20 },
  { item: "society:blueberry", value: 2 },
  { item: "society:blueberry_crate", value: 22 },
  { item: "farmersdelight:cabbage", value: 7 },
  { item: "farmersdelight:cabbage_crate", value: 63 },
  { item: "minecraft:wheat", value: 5 },
  { item: "minecraft:hay_block", value: 41 },
  { item: "minecraft:sugar_cane", value: 1 },
  { item: "quark:sugar_cane_block", value: 11 },
  { item: "brewery:hops", value: 2 },
  { item: "ribbits:toadstool", value: 2 },
  { item: "quark:glow_shroom", value: 2 },
  { item: "farmersdelight:rice", value: 2 },
  { item: "farmersdelight:rice_bag", value: 14 },
  { item: "minecraft:pumpkin", value: 8 },
  { item: "autumnity:large_pumpkin_slice", value: 8 },
  { item: "farmersdelight:pumpkin_slice", value: 2 },
  { item: "supplementaries:flax", value: 7 },
  { item: "supplementaries:flax_block", value: 61 },
  { item: "quark:ancient_fruit", value: 2 },
  { item: "minecraft:chorus_fruit", value: 2 },
  { item: "quark:chorus_fruit_block", value: 14 },
  { item: "farm_and_charm:wild_ribwort", value: 2 },
  { item: "farm_and_charm:wild_nettle", value: 2 },
  { item: "species:ancient_pinecone", value: 3 },
  { item: "windswept:chestnuts", value: 1 },
  { item: "windswept:chestnut_crate", value: 2 },
  { item: "windswept:holly_berries", value: 1 },
  { item: "windswept:holly_berry_basket", value: 3 },
  { item: "minecraft:glow_berries", value: 2 },
  { item: "quark:glowberry_sack", value: 22 },
  { item: "moreminecarts:glass_cactus", value: 2 },
  { item: "vintagedelight:ghost_pepper", value: 4 },
  { item: "vintagedelight:ghost_pepper_crate", value: 32 },
  { item: "vintagedelight:cucumber", value: 7 },
  { item: "vintagedelight:cucumber_crate", value: 65 },
  { item: "society:tubabacco_leaf", value: 9 },
  { item: "society:tubabacco_leaf_block", value: 78 },
  { item: "minecraft:torchflower", value: 13 },
  { item: "minecraft:pitcher_plant", value: 6 },
  { item: "society:ancient_fruit", value: 13 },
  { item: "society:ancient_fruit_crate", value: 115 },
  { item: "atmospheric:aloe_leaves", value: 2 },
  { item: "atmospheric:aloe_bundle", value: 14 },
  // Tree fruits
  { item: "pamhc2trees:bananaitem", value: 2 },
  { item: "pamhc2trees:lycheeitem", value: 2 },
  { item: "pamhc2trees:hazelnutitem", value: 3 },
  { item: "pamhc2trees:mangoitem", value: 6 },
  { item: "pamhc2trees:peachitem", value: 6 },
  { item: "pamhc2trees:pawpawitem", value: 8 },
  { item: "pamhc2trees:plumitem", value: 10 },
  { item: "atmospheric:orange", value: 10 },
  { item: "pamhc2trees:orangeitem", value: 10 },
  { item: "atmospheric:orange_crate", value: 86 },
  { item: "atmospheric:blood_orange", value: 11 },
  { item: "atmospheric:blood_orange_crate", value: 101 },
  { item: "pamhc2trees:lemonitem", value: 16 },
  { item: "pamhc2trees:dragonfruititem", value: 13 },
  { item: "atmospheric:passion_fruit", value: 13 },
  { item: "atmospheric:passion_fruit_crate", value: 115 },
  { item: "atmospheric:shimmering_passion_fruit", value: 16 },
  { item: "atmospheric:shimmering_passion_fruit_crate", value: 144 },
  { item: "pamhc2trees:cinnamonitem", value: 16 },
  { item: "society:ground_cinnamon", value: 8 },
  { item: "pamhc2trees:starfruititem", value: 20 },
];
// Animal Products
global.animalProducts = [
  // Eggs
  { item: "minecraft:egg", value: 1 },
  { item: "untitledduckmod:duck_egg", value: 1 },
  { item: "untitledduckmod:goose_egg", value: 2 },
  { item: "autumnity:turkey_egg", value: 3 },
  { item: "minecraft:turtle_egg", value: 6 },
  { item: "minecraft:sniffer_egg", value: 19 },
  { item: "species:petrified_egg", value: 26 },
  { item: "society:large_egg", value: 2 },
  { item: "society:large_duck_egg", value: 3 },
  { item: "society:large_goose_egg", value: 6 },
  { item: "society:large_turkey_egg", value: 13 },
  { item: "farmlife:galliraptor_egg", value: 26 },
  { item: "society:large_galliraptor_egg", value: 102 },
  { item: "species:birt_egg", value: 1 },
  { item: "species:wraptor_egg", value: 4 },
  { item: "species:springling_egg", value: 13 },
  { item: "society:penguin_egg", value: 19 },
  { item: "society:flamingo_egg", value: 38 },
  // Milk
  { item: "society:sheep_milk", value: 1 },
  { item: "society:milk", value: 2 },
  { item: "society:grain_milk", value: 2 },
  { item: "society:buffalo_milk", value: 6 },
  { item: "society:goat_milk", value: 10 },
  { item: "society:warped_milk", value: 10 },
  { item: "society:amethyst_milk", value: 14 },
  { item: "society:tri_bull_milk", value: 19 },
  { item: "society:large_sheep_milk", value: 3 },
  { item: "society:large_milk", value: 6 },
  { item: "society:large_grain_milk", value: 9 },
  { item: "society:large_buffalo_milk", value: 26 },
  { item: "society:large_goat_milk", value: 38 },
  { item: "society:large_warped_milk", value: 38 },
  { item: "society:large_amethyst_milk", value: 58 },
  { item: "society:large_tri_bull_milk", value: 77 },
  // Basic Raw
  { item: "minecraft:beef", value: 2 },
  { item: "minecraft:porkchop", value: 3 },
  { item: "snowpig:frozen_porkchop", value: 6 },
  { item: "minecraft:mutton", value: 2 },
  { item: "minecraft:chicken", value: 1 },
  { item: "aquaculture:fish_fillet_raw", value: 1 },
  { item: "untitledduckmod:raw_duck", value: 2 },
  // Advanced Raw
  { item: "quark:crab_leg", value: 2 },
  { item: "quark:crab_shell", value: 4 },
  { item: "meadow:raw_buffalo_meat", value: 3 },
  { item: "farmersdelight:ham", value: 6 },
  { item: "minecraft:rabbit", value: 6 },
  { item: "beachparty:raw_mussel_meat", value: 2 },
  { item: "untitledduckmod:raw_goose", value: 2 },
  { item: "autumnity:turkey", value: 3 },
  { item: "atmospheric:carmine_husk", value: 1 },
  { item: "crabbersdelight:raw_squid_tentacles", value: 2 },
  { item: "crabbersdelight:squid_barrel", value: 14 },
  { item: "crabbersdelight:raw_glow_squid_tentacles", value: 3 },
  { item: "crabbersdelight:glow_squid_barrel", value: 29 },
  { item: "crabbersdelight:raw_frog_leg", value: 4 },
  { item: "windswept:goat", value: 4 },
  { item: "crabbersdelight:frog_leg_barrel", value: 36 },
  { item: "farmlife:galliraptor", value: 90 },
  { item: "farmlife:tribull_shank", value: 180 },
  { item: "wildernature:cassowary_meat", value: 9 },
  { item: "wildernature:venison", value: 5 },
  { item: "wildernature:bison_meat", value: 6 },
  { item: "wildernature:pelican_meat", value: 3 },
  // Advanced Cooked
  { item: "snowpig:frozen_ham", value: 13 },
  { item: "buzzier_bees:glazed_porkchop", value: 14 },
  // Bee
  { item: "minecraft:honey_bottle", value: 1 },
  { item: "minecraft:honey_block", value: 2 },
  { item: "minecraft:honeycomb", value: 1 },
  { item: "minecraft:honeycomb_block", value: 2 },
  { item: "buzzier_bees:bee_bottle", value: 2 },
  { item: "etcetera:cotton_flower", value: 2 },
  { item: "society:butterfly_amber", value: 6 },
  { item: "society:moth_pollen", value: 13 },
  // Misc
  { item: "minecraft:leather", value: 1 },
  { item: "netherdepthsupgrade:soul_sucker_leather", value: 2 },
  { item: "netherdepthsupgrade:fortress_grouper_plate", value: 2 },
  { item: "quark:bonded_leather", value: 7 },
  { item: "minecraft:rabbit_hide", value: 1 },
  { item: "quark:bonded_rabbit_hide", value: 11 },
  { item: "minecraft:rabbit_foot", value: 102 },
  { item: "society:truffle", value: 51 },
  { item: "species:ichor_bottle", value: 26 },
  { item: "society:fine_wool", value: 26 },
  { item: "minecraft:feather", value: 2 },
  { item: "untitledduckmod:duck_feather", value: 6 },
  { item: "untitledduckmod:goose_foot", value: 10 },
  { item: "snuffles:snuffle_fluff", value: 2 },
  { item: "snuffles:frosty_fluff", value: 6 },
];

/**
 * Preserves
 * Formula: Ingredient * 20
 */
global.fruits = [
  {
    item: "minecraft:sweet_berries",
    altPreserveOutput: "vintagedelight:sweet_berry_mason_jar",
    value: 4,
  },
  { item: "autumnity:foul_berries", value: 1 },
  { item: "atmospheric:currant", value: 1 },
  {
    item: "atmospheric:yucca_fruit",
    altPreserveOutput: "society:yucca_preserves",
    value: 8,
  },
  {
    item: "minecraft:apple",
    altPreserveOutput: "vintagedelight:apple_sauce_mason_jar",
    value: 8,
  },
  {
    item: "minecraft:melon_slice",
    altPreserveOutput: "society:melon_preserves",
    value: 9,
  },
  {
    item: "vintagedelight:gearo_berry",
    altPreserveOutput: "vintagedelight:gearo_berry_mason_jar",
    value: 24,
  },
  { item: "minecraft:chorus_fruit", value: 2 },
  { item: "pamhc2trees:lycheeitem", value: 2 },
  { item: "pamhc2trees:bananaitem", value: 2 },
  { item: "farm_and_charm:strawberry", value: 2 },
  {
    item: "minecraft:glow_berries",
    altPreserveOutput: "vintagedelight:glow_berry_mason_jar",
    value: 24,
  },
  { item: "vinery:cherry", value: 1 },
  { item: "society:blueberry", value: 2 },
  { item: "pamhc2trees:mangoitem", value: 6 },
  { item: "pamhc2trees:peachitem", value: 6 },
  { item: "pamhc2trees:pawpawitem", value: 8 },
  { item: "pamhc2trees:plumitem", value: 10 },
  { item: "society:ancient_fruit", value: 13 },
  { item: "atmospheric:orange", value: 10 },
  { item: "pamhc2trees:dragonfruititem", value: 13 },
  { item: "atmospheric:passion_fruit", value: 13 },
  { item: "pamhc2trees:lemonitem", value: 16 },
  { item: "pamhc2trees:starfruititem", value: 20 },
  { item: "society:salmonberry", value: 2 },
  { item: "society:boysenberry", value: 2 },
  { item: "society:cranberry", value: 2 },
  { item: "society:crystalberry", value: 2 },
  { item: "windswept:wild_berries", value: 1 },
];
global.preserves = [
  { item: "society:red_grape_preserves", value: 12 },
  { item: "society:white_grape_preserves", value: 12 },
  { item: "society:onion_preserves", value: 24 },
  { item: "society:aloe_preserves", value: 30 },
  { item: "society:pumpkin_preserves", value: 36 },
  { item: "society:sweet_potato_preserves", value: 36 },
  { item: "society:carrot_preserves", value: 41 },
  { item: "vintagedelight:nut_mash_mason_jar", value: 42 },
  { item: "society:potato_preserves", value: 42 },
  { item: "society:beetroot_preserves", value: 42 },
  { item: "society:ginger_preserves", value: 42 },
  { item: "society:tomato_preserves", value: 45 },
  { item: "society:garlic_preserves", value: 47 },
  { item: "society:corn_preserves", value: 48 },
  { item: "society:hazelnut_mash", value: 54 },
  { item: "society:bell_pepper_preserves", value: 54 },
  { item: "vintagedelight:relish_mason_jar", value: 144 },
  { item: "vintagedelight:pepper_jam_mason_jar", value: 60 },
  { item: "society:cauliflower_preserves", value: 78 },
  { item: "society:eggplant_preserves", value: 69 },
  { item: "society:turnip_preserves", value: 78 },
  { item: "society:zucchini_preserves", value: 144 },
  { item: "society:broccoli_preserves", value: 108 },
];
global.dehydrated = [
  { item: "society:raisins", value: 36 },
  { item: "society:nether_raisins", value: 40 },
];
global.fruits.forEach((fruit) => {
  let itemId = fruit.item.path;
  if (itemId.includes("item")) itemId = itemId.substring(0, itemId.length - 4);
  global.preserves.push({
    item: fruit.altPreserveOutput ? fruit.altPreserveOutput : `society:${itemId}_preserves`,
    value: fruit.value * 15 + 64,
  });
  global.dehydrated.push({
    item: `society:dried_${itemId}`,
    value: fruit.value * 14 + 64,
  });
});
global.mushrooms = [
  { item: "minecraft:brown_mushroom", value: 1 },
  { item: "minecraft:red_mushroom", value: 1 },
  { item: "minecraft:crimson_fungus", value: 2 },
  { item: "minecraft:warped_fungus", value: 2 },
  { item: "verdantvibes:bracket_mushroom", value: 3 },
  { item: "species:alphacene_mushroom", value: 3 },
  { item: "quark:glow_shroom", value: 2 },
  { item: "ribbits:toadstool", value: 2 },
  // Tag equivalent
  { item: "botania:shimmering_mushrooms", value: 16 },
];
global.mushrooms.forEach((shroom) => {
  let itemId = shroom.item.path;
  global.dehydrated.push({
    item: `society:dried_${itemId}`,
    value: shroom.value * 12 + 32,
  });
});
/**
 * Aging Cask
 * Formula: input * 4
 *
 * Ancient Cask
 * Formula: aging cask input * 16
 *
 * Mayo: (egg with floor of 8)  * 8
 */
global.artisanGoods = [
  { item: "society:battery", value: 40 },
  { item: "society:canvas", value: 8 },
  { item: "society:merino_wool", value: 410 },
  { item: "botania:mana_string", value: 38 },
  { item: "botania:manaweave_cloth", value: 614 },
  { item: "society:mayonnaise", value: 3 },
  { item: "society:duck_mayonnaise", value: 6 },
  { item: "society:goose_mayonnaise", value: 13 },
  { item: "society:turkey_mayonnaise", value: 26 },
  { item: "society:galliraptor_mayonnaise", value: 205 },
  { item: "society:parrot_mayonnaise", value: 51 },
  { item: "society:turtle_mayonnaise", value: 102 },
  { item: "society:sniffer_mayonnaise", value: 154 },
  { item: "society:petrified_mayonnaise", value: 205 },
  { item: "society:supreme_mayonnaise", value: 1000 },
  { item: "society:golden_mayonnaise", value: 1229 },
  { item: "society:dragon_mayonnaise", value: 1229 },
  { item: "society:large_mayonnaise", value: 13 },
  { item: "society:large_duck_mayonnaise", value: 26 },
  { item: "society:large_goose_mayonnaise", value: 51 },
  { item: "society:large_turkey_mayonnaise", value: 102 },
  { item: "society:large_galliraptor_mayonnaise", value: 819 },
  { item: "society:birt_mayonnaise", value: 10 },
  { item: "society:wraptor_mayonnaise", value: 32 },
  { item: "society:springling_mayonnaise", value: 102 },
  { item: "society:penguin_mayonnaise", value: 154 },
  { item: "society:flamingo_mayonnaise", value: 307 },
  { item: "society:cruncher_mayonnaise", value: 410 },
  { item: "society:oak_resin", value: 5 },
  { item: "society:maple_syrup", value: 19 },
  { item: "society:pine_tar", value: 5 },
  { item: "society:sap", value: 2 },
  { item: "society:rubber", value: 3 },
  { item: "society:aged_cheese_block", value: 58 },
  { item: "society:aged_goat_cheese_block", value: 346 },
  { item: "society:aged_warped_cheese_block", value: 346 },
  { item: "society:aged_buffalo_cheese_block", value: 230 },
  { item: "society:aged_sheep_cheese_block", value: 29 },
  { item: "society:aged_grain_cheese_block", value: 79 },
  { item: "society:aged_amethyst_cheese_block", value: 518 },
  { item: "society:aged_tribull_cheese_wheel", value: 691 },
  { item: "society:double_aged_cheese_block", value: 173 },
  { item: "society:double_aged_goat_cheese_block", value: 1037 },
  { item: "society:double_aged_warped_cheese_block", value: 1037 },
  { item: "society:double_aged_buffalo_cheese_block", value: 691 },
  { item: "society:double_aged_sheep_cheese_block", value: 86 },
  { item: "society:double_aged_grain_cheese_block", value: 238 },
  { item: "society:double_aged_amethyst_cheese_block", value: 1555 },
  { item: "society:double_aged_tribull_cheese_wheel", value: 2074 },
];

// Ice value = 8
// Snow value = 2
global.cocktails = [
  { item: "beachparty:coconut_cocktail", value: 3 },
  { item: "beachparty:refreshing_drink", value: 26 },
  { item: "beachparty:cocoa_cocktail", value: 3 },
  { item: "beachparty:sweetberries_cocktail", value: 5 },
  { item: "beachparty:pumpkin_cocktail", value: 13 },
  { item: "beachparty:honey_cocktail", value: 42 },
  { item: "beachparty:melon_cocktail", value: 3 },
  { item: "beachparty:sweetberry_icecream", value: 4 },
  { item: "beachparty:coconut_icecream", value: 2 },
  { item: "beachparty:chocolate_icecream", value: 1 },
  { item: "society:blueberry_icecream", value: 6 },
  { item: "beachparty:icecream_cactus", value: 4 },
  { item: "beachparty:icecream_melon", value: 3 },
  { item: "beachparty:icecream_coconut", value: 2 },
  { item: "beachparty:icecream_chocolate", value: 1 },
  { item: "beachparty:icecream_sweetberries", value: 2 },
  { item: "beachparty:sweetberry_milkshake", value: 14 },
  { item: "beachparty:coconut_milkshake", value: 11 },
  { item: "beachparty:chocolate_milkshake", value: 8 },
];
// Steamed milk = 32
global.herbalBrews = [
  { item: "herbalbrews:coffee", value: 5 },
  { item: "herbalbrews:hazelnut_coffee", value: 9 },
  { item: "herbalbrews:cinnamon_coffee", value: 14 },
  { item: "herbalbrews:milk_coffee", value: 11 },
  { item: "herbalbrews:yerba_mate_tea", value: 1 },
  { item: "herbalbrews:hibiscus_tea", value: 2 },
  { item: "herbalbrews:rooibos_tea", value: 2 },
  { item: "herbalbrews:lavender_tea", value: 2 },
  { item: "windswept:lavender_tea", value: 8 },
  { item: "windswept:ginger_tea", value: 13 },
  { item: "herbalbrews:green_tea", value: 14 },
  { item: "herbalbrews:black_tea", value: 130 },
  { item: "herbalbrews:chai_tea", value: 264 },
  { item: "herbalbrews:oolong_tea", value: 384 },
  { item: "herbalbrews:dried_green_tea", value: 12 },
  { item: "herbalbrews:dried_black_tea", value: 108 },
  { item: "herbalbrews:dried_oolong_tea", value: 320 },
  { item: "society:espresso", value: 13 },
  { item: "society:latte", value: 58 },
  { item: "society:mocha", value: 51 },
  { item: "society:dirty_chai", value: 557 },
  { item: "society:bowl_of_soul", value: 24 },
  { item: "society:truffle_tea", value: 205 },
];
// Logs
global.logs = [
  { item: "minecraft:oak_log", value: 1 },
  { item: "minecraft:stripped_oak_log", value: 1 },
  { item: "minecraft:spruce_log", value: 1 },
  { item: "minecraft:stripped_spruce_log", value: 1 },
  { item: "minecraft:birch_log", value: 1 },
  { item: "minecraft:stripped_birch_log", value: 1 },
  { item: "minecraft:jungle_log", value: 1 },
  { item: "minecraft:stripped_jungle_log", value: 1 },
  { item: "minecraft:dark_oak_log", value: 1 },
  { item: "minecraft:stripped_dark_oak_log", value: 1 },
  { item: "quark:blossom_log", value: 1 },
  { item: "quark:stripped_blossom_log", value: 1 },
  { item: "beachparty:palm_log", value: 1 },
  { item: "beachparty:stripped_palm_log", value: 1 },
  { item: "meadow:pine_log", value: 1 },
  { item: "meadow:stripped_pine_log", value: 1 },
  { item: "atmospheric:aspen_log", value: 1 },
  { item: "atmospheric:stripped_aspen_log", value: 1 },
  { item: "atmospheric:watchful_aspen_log", value: 1 },
  { item: "atmospheric:crustose_log", value: 1 },
  { item: "atmospheric:stripped_crustose_log", value: 1 },
  { item: "autumnity:maple_log", value: 1 },
  { item: "autumnity:stripped_maple_log", value: 1 },
  { item: "atmospheric:kousa_log", value: 1 },
  { item: "atmospheric:stripped_kousa_log", value: 1 },
  { item: "atmospheric:laurel_log", value: 1 },
  { item: "atmospheric:stripped_laurel_log", value: 1 },
  { item: "atmospheric:yucca_log", value: 1 },
  { item: "atmospheric:stripped_yucca_log", value: 1 },
  { item: "atmospheric:morado_log", value: 1 },
  { item: "atmospheric:stripped_morado_log", value: 1 },
  { item: "atmospheric:rosewood_log", value: 1 },
  { item: "atmospheric:stripped_rosewood_log", value: 1 },
  { item: "atmospheric:grimwood_log", value: 1 },
  { item: "atmospheric:stripped_grimwood_log", value: 1 },
  { item: "botania:dreamwood_log", value: 3 },
  { item: "botania:stripped_dreamwood_log", value: 3 },
  { item: "botania:livingwood_log", value: 2 },
  { item: "botania:stripped_livingwood_log", value: 2 },
  { item: "vinery:dark_cherry_log", value: 1 },
  { item: "vinery:stripped_dark_cherry_log", value: 1 },
  { item: "vinery:apple_log", value: 1 },
  { item: "minecraft:acacia_log", value: 1 },
  { item: "minecraft:stripped_acacia_log", value: 1 },
  { item: "minecraft:mangrove_log", value: 1 },
  { item: "minecraft:stripped_mangrove_log", value: 1 },
  { item: "minecraft:cherry_log", value: 1 },
  { item: "minecraft:stripped_cherry_log", value: 1 },
  { item: "quark:azalea_log", value: 1 },
  { item: "quark:stripped_azalea_log", value: 1 },
  { item: "quark:ancient_log", value: 1 },
  { item: "quark:stripped_ancient_log", value: 1 },
  { item: "betterarcheology:rotten_log", value: 1 },
  { item: "minecraft:warped_stem", value: 2 },
  { item: "minecraft:stripped_warped_stem", value: 2 },
  { item: "minecraft:crimson_stem", value: 2 },
  { item: "minecraft:stripped_crimson_stem", value: 2 },
  { item: "vintagedelight:magic_vine", value: 3 },
  { item: "vintagedelight:stripped_magic_vine", value: 3 },
  { item: "vanillabackport:pale_oak_log", value: 1 },
  { item: "vanillabackport:stripped_pale_oak_log", value: 1 },
  { item: "farmersdelight:straw", value: 1 },
  { item: "farmersdelight:straw_bale", value: 3 },
  { item: "farmersdelight:canvas", value: 1 },
  { item: "windswept:stripped_holly_log", value: 1 },
  { item: "windswept:holly_log", value: 1 },
  { item: "windswept:chestnut_log", value: 1 },
  { item: "windswept:stripped_chestnut_log", value: 1 },
  { item: "windswept:pine_log", value: 1 },
  { item: "windswept:stripped_pine_log", value: 1 },
  { item: "windswept:weathered_pine_log", value: 1 },
];

/**
 * Wine
 * Formula: (72 + sum ingredient val) * 4
 */
global.wines = [
  { item: "society:forks_of_blue", value: 58 },
  { item: "vinery:jo_special_mixture", value: 163 },
  { item: "vinery:cristel_wine", value: 58 },
  { item: "vinery:creepers_crush", value: 34 },
  { item: "vinery:villagers_fright", value: 38 },
  { item: "vinery:glowing_wine", value: 144 },
  { item: "vinery:mead", value: 58 },
  { item: "vinery:bottle_mojang_noir", value: 48 },
  { item: "vinery:eiswein", value: 182 },
  { item: "nethervinery:blazewine_pinot", value: 221 },
  { item: "nethervinery:netherite_nectar", value: 438 },
  { item: "nethervinery:ghastly_grenache", value: 48 },
  { item: "nethervinery:lava_fizz", value: 72 },
  { item: "nethervinery:nether_fizz", value: 125 },
  { item: "nethervinery:improved_lava_fizz", value: 80 },
  { item: "nethervinery:improved_nether_fizz", value: 140 },
  { item: "vinery:chorus_wine", value: 48 },
  { item: "vinery:cherry_wine", value: 43 },
  { item: "vinery:magnetic_wine", value: 38 },
  { item: "vinery:noir_wine", value: 144 },
  { item: "vinery:lilitu_wine", value: 182 },
  { item: "vinery:mellohi_wine", value: 438 },
  { item: "vinery:stal_wine", value: 60 },
  { item: "vinery:strad_wine", value: 34 },
  { item: "vinery:solaris_wine", value: 58 },
  { item: "vinery:bolvar_wine", value: 50 },
  { item: "vinery:aegis_wine", value: 106 },
  { item: "vinery:clark_wine", value: 48 },
  { item: "vinery:chenet_wine", value: 106 },
  { item: "vinery:kelp_cider", value: 126 },
  { item: "vinery:apple_wine", value: 38 },
  { item: "vinery:apple_cider", value: 259 },
  { item: "vinery:red_wine", value: 34 },
  { item: "society:good_catawba", value: 50 },
  { item: "vinery:jellie_wine", value: 605 },
  { item: "society:ancient_vespertine", value: 182 },
  { item: "society:dewy_star", value: 269 },
  { item: "society:ancient_cider", value: 342 },
  { item: "society:star_coquito", value: 600 },
  { item: "society:nutty_basil", value: 62 },
];

global.wines.forEach((wine) => {
  global.artisanGoods.push({
    item: `society:aged_${wine.item.path}`,
    value: wine.value * 4,
  });
  global.artisanGoods.push({
    item: `society:double_aged_${wine.item.path}`,
    value: wine.value * 16,
  });
});

// Brewery
const brewingStationRecipes = [
  { item: "brewery:beer_nettle", value: 15 },
  { item: "brewery:beer_hops", value: 14 },
  { item: "brewery:beer_wheat", value: 28 },
  { item: "brewery:beer_barley", value: 13 },
  { item: "brewery:beer_oat", value: 23 },
  { item: "brewery:beer_haley", value: 15 },
  { item: "society:beer_london", value: 90 },
  { item: "society:beer_attunecore", value: 32 },
  { item: "brewery:whiskey_carrasconlabel", value: 34 },
  { item: "brewery:whiskey_maggoallan", value: 43 },
  { item: "brewery:whiskey_jamesons_malt", value: 22 },
  { item: "brewery:whiskey_smokey_reverie", value: 32 },
  { item: "brewery:whiskey_highland_hearth", value: 37 },
  { item: "brewery:whiskey_ak", value: 29 },
  { item: "brewery:whiskey_cristelwalker", value: 26 },
  { item: "brewery:whiskey_lilitusinglemalt", value: 27 },
  { item: "brewery:whiskey_jojannik", value: 11 },
  { item: "brewery:dark_brew", value: 243 },
  { item: "society:starcardi", value: 68 },
];
global.brews = [];
brewingStationRecipes.forEach((recipe) => {
  global.brews.push({
    item: recipe.item,
    value: recipe.value * 3,
  });
});

global.brews.forEach((brew) => {
  global.artisanGoods.push({
    item: `society:aged_${brew.item.path}`,
    value: brew.value * 4,
  });
  global.artisanGoods.push({
    item: `society:double_aged_${brew.item.path}`,
    value: brew.value * 16,
  });
});
const miscAged = [
  { item: "vintagedelight:century_egg", value: 192 * 3 },
  { item: "society:energy_drink", value: 39 },
  { item: "society:espresso", value: 13 },
];

miscAged.forEach((brew) => {
  global.artisanGoods.push({
    item: `society:aged_${brew.item.path}`,
    value: brew.value * 4,
  });
  global.artisanGoods.push({
    item: `society:double_aged_${brew.item.path}`,
    value: brew.value * 16,
  });
});

/*
 * Cooking
 * Egg = 4
 * Butter = 32
 * Grain = 28
 * Cheese slice = 48
 * Dough/pasta = 16
 * P. Noodle = 16
 * Vegetable tag = 20
 * Bread = 16
 * Sweet dough = 8
 * Cake dough = 16
 * Yeast = 4
 * Milk = 64
 * Fish = 64
 * Sugar = 3
 * Salt = 2
 * Berry = 8
 * Pie crust = 230
 * Wine = 88 (Adds * 2 to overall price)
 * Beer = 128 (Adds * 2 to overall price)
 * Complexity: 4+ unique ingr. OR nested cooking ingr. = * 1.5 Mult
 */
global.cooking = [];
// Raw ingredient calculation. Multiplier added before pushing to global.cooking
const craftingTableRecipes = [
  { item: "veggiesdelight:zucchini_sandwich", value: 19 },
  { item: "veggiesdelight:turnip_salad", value: 8 },
  { item: "veggiesdelight:broccoli_salad", value: 13 },
  { item: "autumnity:foul_soup", value: 2 },
  { item: "aquaculture:turtle_soup", value: 39 },
  { item: "aquaculture:sushi", value: 2 },
  { item: "bakery:bread_crate", value: 9 },
  { item: "bakery:sandwich", value: 8 },
  { item: "bakery:chocolate_box", value: 10 },
  { item: "bakery:bread_with_jam", value: 4 },
  { item: "bakery:vegetable_sandwich", value: 6 },
  { item: "brewery:mashed_potatoes", value: 4 },
  { item: "brewery:half_chicken", value: 2 },
  { item: "buzzier_bees:honey_apple", value: 21 },
  { item: "buzzier_bees:honey_bread", value: 3 },
  { item: "crabbersdelight:coral_crunch", value: 7 },
  { item: "crabbersdelight:fish_stick", value: 18 },
  { item: "crabbersdelight:surf_and_turf", value: 10 },
  { item: "crabbersdelight:shrimp_skewer", value: 5 },
  { item: "crabbersdelight:squid_kebob", value: 11 },
  { item: "crabbersdelight:frog_leg_kebob", value: 21 },
  { item: "crabbersdelight:kelp_shake", value: 1 },
  { item: "crabbersdelight:sea_pickle_juice", value: 1 },
  { item: "create:chocolate_glazed_berries", value: 5 },
  { item: "create:bar_of_chocolate", value: 4 },
  { item: "create:sweet_roll", value: 9 },
  { item: "create:builders_tea", value: 49 },
  { item: "create:honeyed_apple", value: 4 },
  { item: "farm_and_charm:farmers_breakfast", value: 55 },
  { item: "farmersdelight:grilled_salmon", value: 16 },
  { item: "farmersdelight:roasted_mutton_chops", value: 19 },
  { item: "farmersdelight:sweet_berry_cheesecake", value: 64 },
  { item: "farmersdelight:nether_salad", value: 40 },
  { item: "farmersdelight:melon_popsicle", value: 7 },
  { item: "farmersdelight:salmon_roll", value: 5 },
  { item: "farmersdelight:cod_roll", value: 8 },
  { item: "farmersdelight:fruit_salad", value: 23 },
  { item: "farmersdelight:mixed_salad", value: 10 },
  { item: "farmersdelight:kelp_roll", value: 10 },
  { item: "farmersdelight:kelp_roll_slice", value: 4 },
  { item: "farmersdelight:cabbage_rolls", value: 14 },
  { item: "farmersdelight:hamburger", value: 21 },
  { item: "farmersdelight:chicken_sandwich", value: 16 },
  { item: "farmersdelight:bacon_sandwich", value: 24 },
  { item: "farmersdelight:mutton_wrap", value: 16 },
  { item: "farmersdelight:stuffed_potato", value: 13 },
  { item: "farmersdelight:barbecue_stick", value: 8 },
  { item: "farmersdelight:egg_sandwich", value: 4 },
  { item: "farmersdelight:steak_and_potatoes", value: 20 },
  { item: "farmersdelight:chocolate_pie", value: 92 },
  { item: "farmersdelight:honey_glazed_ham_block", value: 34 },
  { item: "farmersdelight:shepherds_pie_block", value: 36 },
  { item: "farmersdelight:rice_roll_medley_block", value: 41 },
  { item: "farmersdelight:apple_pie", value: 84 },
  { item: "farmersdelight:melon_juice", value: 30 },
  { item: "farmersdelight:roast_chicken_block", value: 34 },
  { item: "meadow:roasted_ham", value: 15 },
  { item: "meadow:cheese_stick", value: 6 },
  { item: "meadow:cheesecake", value: 5 },
  { item: "meadow:cheese_tart", value: 7 },
  { item: "meadow:cheese_sandwich", value: 2 },
  { item: "meadow:cheese_roll", value: 2 },
  { item: "minecraft:cookie", value: 2 },
  { item: "minecraft:bread", value: 2 },
  { item: "minecraft:pumpkin_pie", value: 12 },
  { item: "minecraft:mushroom_stew", value: 2 },
  { item: "minecraft:cake", value: 44 },
  { item: "minecraft:rabbit_stew", value: 33 },
  { item: "netherdepthsupgrade:lava_pufferfish_roll", value: 77 },
  { item: "netherdepthsupgrade:obsidianfish_roll", value: 147 },
  { item: "netherdepthsupgrade:searing_cod_roll", value: 77 },
  { item: "netherdepthsupgrade:blazefish_roll", value: 118 },
  { item: "netherdepthsupgrade:magmacubefish_roll", value: 177 },
  { item: "netherdepthsupgrade:glowdine_roll", value: 129 },
  { item: "netherdepthsupgrade:soulsucker_roll", value: 91 },
  { item: "netherdepthsupgrade:warped_kelp_roll", value: 13 },
  { item: "netherdepthsupgrade:warped_kelp_roll_slice", value: 4 },
  { item: "netherdepthsupgrade:nether_rice_roll_medley_block", value: 1015 },
  { item: "refurbished_furniture:bread_slice", value: 1 },
  { item: "refurbished_furniture:glow_berry_jam_toast", value: 7 },
  { item: "refurbished_furniture:sweet_berry_jam_toast", value: 3 },
  { item: "refurbished_furniture:cheese_toastie", value: 2 },
  { item: "snowyspirit:gingerbread_cookie", value: 1 },
  { item: "snowyspirit:candy_cane", value: 1 },
  { item: "windswept:candy_cane_block", value: 5 },
  { item: "snowyspirit:eggnog", value: 6 },
  { item: "supplementaries:candy", value: 3 },
  { item: "vintagedelight:salted_cod", value: 2 },
  { item: "vintagedelight:salted_salmon", value: 4 },
  { item: "vintagedelight:honey_roasted_peanut", value: 6 },
  { item: "vintagedelight:magic_peanut", value: 140 },
  { item: "society:tubasmoke_stick", value: 28 },
  { item: "society:tubasmoke_carton", value: 248 },
  { item: "vintagedelight:fruity_granola_bar", value: 3 },
  { item: "vintagedelight:deluxe_granola_bar", value: 6 },
  { item: "vintagedelight:chocolate_nut_granola_bar", value: 6 },
  { item: "vintagedelight:stuffed_burrito", value: 38 },
  { item: "vintagedelight:deluxe_burger", value: 45 },
  { item: "vintagedelight:cheese_burger", value: 33 },
  { item: "vintagedelight:pb_j", value: 29 },
  { item: "vintagedelight:cucumber_salad", value: 45 },
  { item: "vintagedelight:oatmeal_cookie", value: 1 },
  { item: "society:energy_drink", value: 39 },
  { item: "society:death_liquid", value: 130 },
  { item: "unusualfishmod:odd_fishsticks", value: 13 },
  { item: "unusualfishmod:weird_goldfish", value: 9 },
  { item: "unusualfishmod:pickledish", value: 18 },
  { item: "unusualfishmod:strange_broth", value: 17 },
  { item: "unusualfishmod:unusual_sandwich", value: 18 },
  { item: "unusualfishmod:raw_aero_mono_stick", value: 18 },
  { item: "atmospheric:candied_orange_slices", value: 14 },
  { item: "atmospheric:orange_sorbet", value: 15 },
  { item: "atmospheric:currant_muffin", value: 4 },
  { item: "atmospheric:aloe_gel_bottle", value: 18 },
  { item: "atmospheric:yucca_gateau", value: 52 },
  { item: "atmospheric:aloe_gel_block", value: 72 },
  { item: "atmospheric:passion_fruit_sorbet", value: 19 },
  { item: "atmospheric:passion_fruit_tart", value: 18 },
  { item: "veggiesdelight:beetroot_brownie_tray", value: 20 },
  { item: "veggiesdelight:sweet_potato_cupcake", value: 4 },
  { item: "veggiesdelight:carrot_cake", value: 36 },
  { item: "veggiesdelight:sweet_potato_pie", value: 68 },
  { item: "veggiesdelight:cesar_salad", value: 24 },
  { item: "veggiesdelight:dandelion_and_eggs", value: 8 },
  { item: "veggiesdelight:chicken_fajitas_wrap", value: 12 },
  { item: "veggiesdelight:cauliflower_burger", value: 23 },
  { item: "windswept:spicy_snow_cone", value: 9 },
  { item: "windswept:sweet_snow_cone", value: 5 },
  { item: "windswept:mutton_pie", value: 16 },
  { item: "windswept:minty_snow_cone", value: 4 },
];
craftingTableRecipes.forEach((recipe) => {
  global.cooking.push({
    item: recipe.item,
    value: Math.floor(recipe.value * 1.4),
  });
});

const cheeses = [
  // Values shouldn't be multiplied for balance
  { item: "vintagedelight:honey_mason_jar", value: 5 },
  { item: "meadow:piece_of_sheep_cheese", value: 2 },
  { item: "meadow:sheep_cheese_block", value: 10 },
  { item: "meadow:piece_of_cheese", value: 5 },
  { item: "meadow:cheese_block", value: 19 },
  { item: "meadow:piece_of_grain_cheese", value: 7 },
  { item: "meadow:grain_cheese_block", value: 26 },
  { item: "meadow:piece_of_buffalo_cheese", value: 19 },
  { item: "meadow:buffalo_cheese_block", value: 77 },
  { item: "meadow:piece_of_goat_cheese", value: 29 },
  { item: "meadow:goat_cheese_block", value: 115 },
  { item: "meadow:piece_of_warped_cheese", value: 29 },
  { item: "meadow:warped_cheese_block", value: 115 },
  { item: "meadow:piece_of_amethyst_cheese", value: 43 },
  { item: "meadow:amethyst_cheese_block", value: 173 },
  { item: "farmlife:tribull_cheese_wedge", value: 58 },
  { item: "farmlife:tribull_cheese_wheel", value: 230 },
];
cheeses.forEach((recipe) => {
  global.cooking.push({
    item: recipe.item,
    value: recipe.value,
  });
});

// Raw ingredient calculation. Multiplier added before pushing to global.cooking
let fermentingRecipes = [
  { item: "windswept:pinecone_jam_bottle", value: 64 },
  { item: "vintagedelight:pickled_onion", value: 4 },
  { item: "vintagedelight:pickle", value: 22 },
  { item: "vintagedelight:century_egg", value: 41 },
  { item: "vintagedelight:surstromming", value: 14 },
  { item: "vintagedelight:pickled_pepper", value: 11 },
  { item: "society:truffle_oil", value: 826 },
  { item: "vintagedelight:overnight_oats", value: 36 },
  { item: "vintagedelight:pickled_beetroot", value: 8 },
  { item: "vintagedelight:pickled_egg", value: 5 },
  { item: "vintagedelight:vinegar_mason_jar", value: 51 },
  { item: "vintagedelight:pickled_pitcher_pod", value: 19 },
  { item: "vintagedelight:kimchi", value: 21 },
  { item: "veggiesdelight:fermented_garlic_honey", value: 47 },
  { item: "supplementaries:lumisene_bottle", value: 7 },
];
global.picklableVegetables = [
  { item: "farmersdelight:pumpkin_slice", value: 2 },
  { item: "farm_and_charm:lettuce", value: 2 },
  { item: "minecraft:potato", value: 2 },
  { item: "minecraft:carrot", value: 2 },
  { item: "minecraft:golden_carrot", value: 15 },
  { item: "snowyspirit:ginger", value: 2 },
  { item: "veggiesdelight:garlic", value: 3 },
  { item: "veggiesdelight:bellpepper", value: 3 },
  { item: "society:eggplant", value: 4 },
  { item: "veggiesdelight:cauliflower", value: 5 },
  { item: "veggiesdelight:zucchini", value: 7 },
  { item: "veggiesdelight:turnip", value: 4 },
  { item: "veggiesdelight:broccoli", value: 5 },
];
global.picklableVegetables.forEach((recipe) =>
  fermentingRecipes.push({
    item: `society:pickled_${recipe.item.path}`,
    value: recipe.value,
  })
);

fermentingRecipes.forEach((recipe) => {
  global.cooking.push({
    item: recipe.item,
    value: recipe.value * 3,
  });
});

// Raw ingredient calculation. Multiplier added before pushing to global.cooking
const furnaceRecipes = [
  { item: "buzzier_bees:crystallized_honey_block", value: 4 },
  { item: "unusualfishmod:cooked_unusual_fillet", value: 10 },
  { item: "vintagedelight:ghost_charcoal", value: 5 },
  { item: "pamhc2trees:roastedhazelnutitem", value: 5 },
  { item: "vintagedelight:roasted_peanut", value: 4 },
  { item: "atmospheric:roasted_yucca_fruit", value: 1 },
  { item: "atmospheric:roasted_yucca_bundle", value: 11 },
  { item: "atmospheric:roasted_yucca_cask", value: 11 },
  { item: "windswept:roasted_chestnuts", value: 1 },
  { item: "windswept:roasted_chestnut_crate", value: 3 },
  { item: "minecraft:cooked_beef", value: 2 },
  { item: "minecraft:baked_potato", value: 4 },
  { item: "minecraft:cooked_porkchop", value: 5 },
  { item: "minecraft:cooked_rabbit", value: 10 },
  { item: "minecraft:cooked_mutton", value: 2 },
  { item: "minecraft:cooked_chicken", value: 1 },
  { item: "crabbersdelight:cooked_clam_meat", value: 1 },
  { item: "crabbersdelight:cooked_frog_leg", value: 6 },
  { item: "windswept:cooked_goat", value: 6 },
  { item: "crabbersdelight:cooked_tropical_fish", value: 11 },
  { item: "crabbersdelight:cooked_squid_tentacles", value: 2 },
  { item: "crabbersdelight:cooked_glow_squid_tentacles", value: 5 },
  { item: "farmlife:cooked_galliraptor", value: 135 },
  { item: "farmlife:cooked_tribull_shank", value: 270 },
  { item: "wildernature:cooked_bison_meat", value: 10 },
  { item: "wildernature:cooked_pelican_meat", value: 5 },
  { item: "wildernature:cooked_venison", value: 7 },
  { item: "wildernature:cooked_cassowary_meat", value: 14 },
  { item: "minecraft:cooked_salmon", value: 4 },
  { item: "quark:cooked_crab_leg", value: 4 },
  { item: "untitledduckmod:cooked_duck", value: 2 },
  { item: "untitledduckmod:cooked_goose", value: 2 },
  { item: "autumnity:cooked_turkey", value: 5 },
  { item: "minecraft:cooked_cod", value: 2 },
  { item: "unusualfishmod:cooked_aero_mono_stick", value: 29 },
  { item: "aquaculture:fish_fillet_cooked", value: 1 },
  { item: "meadow:cooked_buffalo_meat", value: 5 },
  { item: "beachparty:cooked_mussel_meat", value: 2 },
  { item: "farmersdelight:smoked_ham", value: 10 },
  { item: "minecraft:popped_chorus_fruit", value: 2 },
  { item: "vintagedelight:meat_pizza", value: 78 },
  { item: "refurbished_furniture:toast", value: 1 },
  { item: "refurbished_furniture:cooked_vegetable_pizza", value: 62 },
  { item: "veggiesdelight:roasted_garlic_clove", value: 2 },
  { item: "veggiesdelight:smoked_bellpepper", value: 5 },
  { item: "veggiesdelight:baked_sweet_potato", value: 3 },
  { item: "veggiesdelight:mhadjeb", value: 14 },
  { item: "veggiesdelight:garlic_baked_cod", value: 9 },
];
furnaceRecipes.forEach((recipe) => {
  global.cooking.push({
    item: recipe.item,
    value: Math.round(recipe.value * 1.5),
  });
});

// Raw ingredient calculation. Multiplier added before pushing to global.cooking
const dryingRecipes = [
  { item: "society:dried_tubabacco_leaf", value: 13 },
  { item: "brewery:dried_wheat", value: 7 },
  { item: "brewery:dried_barley", value: 2 },
  { item: "brewery:dried_corn", value: 4 },
  { item: "brewery:dried_oat", value: 5 },
];
dryingRecipes.forEach((recipe) => {
  global.cooking.push({
    item: recipe.item,
    value: Math.round(recipe.value * 1.5),
  });
});

// Raw ingredient calculation. Multiplier added before pushing to global.cooking
// Note: Raw value not divided by output count due to effort. Cookies multiplied by 1.5
const cakingStationRecipes = [
  { item: "bakery:apple_cupcake", value: 3 },
  { item: "bakery:strawberry_cake", value: 19 },
  { item: "bakery:strawberry_cupcake", value: 5 },
  { item: "bakery:sweetberry_cupcake", value: 3 },
  { item: "bakery:sweetberry_cake", value: 11 },
  { item: "bakery:chocolate_glazed_cookie", value: 22 },
  { item: "bakery:strawberry_glazed_cookie", value: 6 },
  { item: "bakery:sweetberry_glazed_cookie", value: 5 },
  { item: "bakery:chocolate_cake", value: 57 },
  { item: "bakery:chocolate_gateau", value: 10 },
];
cakingStationRecipes.forEach((recipe) => {
  global.cooking.push({
    item: recipe.item,
    value: recipe.value * 4,
  });
});
const cookingPotRecipes = [
  { item: "windswept:goat_stew", value: 38 },
  { item: "windswept:chestnut_soup", value: 16 },
  { item: "windswept:christmas_pudding", value: 32 },
  { item: "veggiesdelight:garlic_chicken_stew", value: 37 },
  { item: "society:chicken_tortilla_soup", value: 56 },
  { item: "society:mexican_street_corn", value: 32 },
  { item: "farmersdelight:tomato_sauce", value: 10 },
  { item: "minecraft:beetroot_soup", value: 14 },
  { item: "farm_and_charm:strawberry_tea", value: 8 },
  { item: "farm_and_charm:nettle_tea", value: 10 },
  { item: "vintagedelight:cheese_pasta", value: 22 },
  { item: "farm_and_charm:onion_soup", value: 6 },
  { item: "bakery:strawberry_jam", value: 4 },
  { item: "farmersdelight:hot_cocoa", value: 5 },
  { item: "crabbersdelight:seafood_gumbo", value: 24 },
  { item: "candlelight:pasta_with_mozzarella", value: 19 },
  { item: "vintagedelight:pickle_soup", value: 93 },
  { item: "candlelight:tomato_soup", value: 14 },
  { item: "farmersdelight:apple_cider", value: 36 },
  { item: "candlelight:chocolate_mousse", value: 3 },
  { item: "farmersdelight:pasta_with_meatballs", value: 17 },
  { item: "farmersdelight:mushroom_rice", value: 14 },
  { item: "candlelight:salmon_on_white_wine", value: 29 },
  { item: "farmersdelight:squid_ink_pasta", value: 33 },
  { item: "bakery:chocolate_truffle", value: 1 },
  { item: "farmersdelight:beef_stew", value: 13 },
  { item: "crabbersdelight:crab_cakes", value: 25 },
  { item: "farmersdelight:chicken_soup", value: 24 },
  { item: "farmersdelight:baked_cod_stew", value: 51 },
  { item: "crabbersdelight:clam_chowder", value: 34 },
  { item: "crabbersdelight:jar_of_pickles", value: 1 },
  { item: "atmospheric:orange_pudding", value: 33 },
  { item: "farmersdelight:glow_berry_custard", value: 43 },
  { item: "farmersdelight:ratatouille", value: 26 },
  { item: "vintagedelight:nut_milk_bottle", value: 10 },
  { item: "netherdepthsupgrade:baked_soulsucker_stew", value: 245 },
  { item: "netherdepthsupgrade:baked_glowdine_stew", value: 326 },
  { item: "netherdepthsupgrade:baked_magmacubefish_stew", value: 430 },
  { item: "netherdepthsupgrade:baked_blazefish_stew", value: 302 },
  { item: "netherdepthsupgrade:baked_searing_cod_stew", value: 216 },
  { item: "netherdepthsupgrade:baked_obsidianfish_stew", value: 365 },
  { item: "netherdepthsupgrade:baked_lava_pufferfish_stew", value: 216 },
  { item: "bakery:pudding", value: 20 },
  { item: "brewery:dumplings", value: 12 },
  { item: "bakery:hazelnut_ella", value: 200 },
  { item: "bakery:chocolate_jam", value: 17 },
  { item: "candlelight:lasagne", value: 22 },
  { item: "farmersdelight:cooked_rice", value: 3 },
  { item: "crabbersdelight:shrimp_fried_rice", value: 19 },
  { item: "candlelight:khinkali", value: 2 },
  { item: "farmersdelight:vegetable_soup", value: 29 },
  { item: "crabbersdelight:stuffed_nautilus_shell", value: 38 },
  { item: "farm_and_charm:ribwort_tea", value: 10 },
  { item: "candlelight:mushroom_soup", value: 113 },
  { item: "farmersdelight:fried_rice", value: 17 },
  { item: "farm_and_charm:barley_soup", value: 4 },
  { item: "brewery:sausage", value: 11 },
  { item: "farm_and_charm:sausage_with_oat_patty", value: 22 },
  { item: "farmersdelight:noodle_soup", value: 17 },
  { item: "vintagedelight:ghostly_chili", value: 41 },
  { item: "bakery:glowberry_jam", value: 5 },
  { item: "farm_and_charm:simple_tomato_soup", value: 11 },
  { item: "bakery:sweetberry_jam", value: 1 },
  { item: "bakery:chocolate_donut", value: 629 },
  { item: "farmersdelight:vegetable_noodles", value: 27 },
  { item: "vintagedelight:pad_thai", value: 74 },
  { item: "farm_and_charm:potato_soup", value: 7 },
  { item: "bakery:apple_jam", value: 2 },
  { item: "farm_and_charm:corn_grits", value: 14 },
  { item: "farmersdelight:fish_stew", value: 31 },
  { item: "farmersdelight:pasta_with_mutton_chop", value: 22 },
  { item: "candlelight:chicken_teriyaki", value: 6 },
  { item: "crabbersdelight:bisque", value: 38 },
  { item: "farm_and_charm:goulash", value: 62 },
  { item: "crabbersdelight:cooked_shrimp", value: 1 },
  { item: "crabbersdelight:cooked_clawster", value: 3 },
  { item: "crabbersdelight:cooked_crab", value: 2 },
  { item: "crabbersdelight:clam_bake", value: 23 },
  { item: "farmersdelight:pumpkin_soup", value: 28 },
  { item: "farmersdelight:stuffed_pumpkin_block", value: 60 },
  { item: "farmersdelight:dumplings", value: 20 },
  { item: "veggiesdelight:cauliflower_soup", value: 26 },
  { item: "veggiesdelight:mashed_potatoes", value: 22 },
  { item: "veggiesdelight:potato_noodles", value: 9 },
  { item: "veggiesdelight:shakshouka", value: 33 },
  { item: "veggiesdelight:roasted_vegetables", value: 48 },
  { item: "veggiesdelight:stuffed_bellpeppers", value: 29 },
  { item: "veggiesdelight:garlic_rice_with_cauliflower", value: 30 },
  { item: "veggiesdelight:garlic_stuffed_mushrooms", value: 20 },
  { item: "veggiesdelight:fish_and_chips", value: 18 },
  { item: "veggiesdelight:carrot_juice", value: 8 },
  { item: "veggiesdelight:cacciatore", value: 21 },
  { item: "meadow:sausage_with_cheese", value: 34 },
  { item: "veggiesdelight:cauliflower_kuku", value: 44 },
  { item: "veggiesdelight:broccoli_soup", value: 39 },
  { item: "veggiesdelight:stuffed_zucchinis", value: 37 },
  { item: "veggiesdelight:turnip_water", value: 15 },
  { item: "veggiesdelight:turnip_beef_stew", value: 15 },
  { item: "veggiesdelight:pasta_with_broccoli", value: 30 },
];

cookingPotRecipes.forEach((recipe) => {
  global.cooking.push({
    item: recipe.item,
    value: Math.round(recipe.value * 2),
  });
});

// Raw ingredient calculation. Multiplier added before pushing to global.cooking
const stoveRecipes = [
  { item: "candlelight:tropical_fish_supreme", value: 110 },
  { item: "bakery:waffle", value: 5 },
  { item: "farm_and_charm:baked_lamb_ham", value: 12 },
  { item: "bakery:bread", value: 2 },
  { item: "bakery:bun", value: 2 },
  { item: "farm_and_charm:stuffed_rabbit", value: 30 },
  { item: "farm_and_charm:roasted_chicken", value: 14 },
  { item: "candlelight:chicken_with_vegetables", value: 43 },
  { item: "bakery:baguette", value: 3 },
  { item: "bakery:croissant", value: 1 },
  { item: "bakery:glowberry_tart", value: 10 },
  { item: "bakery:grilled_bacon_sandwich", value: 17 },
  { item: "brewery:pork_knuckle", value: 48 },
  { item: "candlelight:roastbeef_with_glazed_carrots", value: 22 },
  { item: "bakery:cornet", value: 6 },
  { item: "farmersdelight:honey_cookie", value: 19 },
  { item: "bakery:misslilitu_biscuit", value: 1 },
  { item: "brewery:gingerbread", value: 16 },
  { item: "farm_and_charm:stuffed_chicken", value: 13 },
  { item: "bakery:grilled_salmon_sandwich", value: 10 },
  { item: "candlelight:beef_wellington", value: 392 },
  { item: "farm_and_charm:potato_with_roast_meat", value: 7 },
  { item: "farm_and_charm:pasta_with_onion_sauce", value: 3 },
  { item: "bakery:toast", value: 5 },
  { item: "bakery:chocolate_tart", value: 10 },
  { item: "farm_and_charm:grandmothers_strawberry_cake", value: 5 },
  { item: "bakery:braided_bread", value: 2 },
  { item: "farm_and_charm:roasted_corn", value: 26 },
  { item: "candlelight:chicken_alfredo", value: 34 },
  { item: "farm_and_charm:farmers_bread", value: 4 },
  { item: "bakery:crusty_bread", value: 2 },
  { item: "bakery:bundt_cake", value: 31 },
  { item: "candlelight:pork_ribs", value: 19 },
  { item: "bakery:apple_pie", value: 26 },
  { item: "farmersdelight:sweet_berry_cookie", value: 2 },
  { item: "society:ancient_cookie", value: 218 },
  { item: "bakery:linzer_tart", value: 310 },
  { item: "brewery:pretzel", value: 4 },
  { item: "bakery:jam_roll", value: 225 },
  { item: "autumnity:pumpkin_bread", value: 28 },
  { item: "veggiesdelight:garlic_bread", value: 10 },
  { item: "veggiesdelight:cauliflower_bread", value: 23 },
  { item: "veggiesdelight:turnip_cake", value: 18 },
];
stoveRecipes.forEach((recipe) => {
  global.cooking.push({
    item: recipe.item,
    value: recipe.value * 3,
  });
});

// Raw ingredient calculation. Multiplier added before pushing to global.cooking
const roasterRecipes = [
  { item: "farm_and_charm:lamb_with_corn", value: 44 },
  { item: "candlelight:bolognese", value: 57 },
  { item: "farm_and_charm:cooked_cod", value: 43 },
  { item: "candlelight:pasta_with_bolognese", value: 76 },
  { item: "candlelight:roasted_lamb_with_lettuce", value: 38 },
  { item: "farm_and_charm:oat_pancake", value: 25 },
  { item: "candlelight:pasta_with_lettuce", value: 26 },
  { item: "farm_and_charm:beef_patty_with_vegetables", value: 32 },
  { item: "candlelight:fillet_steak", value: 82 },
  { item: "farm_and_charm:barley_patties_with_potatoes", value: 24 },
  { item: "farm_and_charm:cooked_salmon", value: 49 },
  { item: "farm_and_charm:chicken_wrapped_in_bacon", value: 40 },
  { item: "candlelight:beef_with_mushroom_in_wine_and_potatoes", value: 158 },
  { item: "candlelight:omelet", value: 7 },
  { item: "brewery:fried_chicken", value: 23 },
  { item: "farm_and_charm:bacon_with_eggs", value: 10 },
];
roasterRecipes.forEach((recipe) => {
  global.cooking.push({
    item: recipe.item,
    value: recipe.value * 3,
  });
});

// Raw ingredient calculation. Multiplier added before pushing to global.cooking
const mixingBowlRecipes = [
  { item: "brewery:potato_salad", value: 25 },
  { item: "farm_and_charm:oatmeal_with_strawberries", value: 17 },
  { item: "farm_and_charm:farmer_salad", value: 18 },
  { item: "candlelight:mozzarella", value: 4 },
  { item: "candlelight:beetroot_salad", value: 11 },
  { item: "candlelight:harvest_plate", value: 7 },
  { item: "farm_and_charm:butter", value: 5 },
  { item: "candlelight:beef_tartare", value: 4 },
  { item: "candlelight:salad", value: 8 },
  { item: "candlelight:fresh_garden_salad", value: 11 },
  { item: "candlelight:tomato_mozzarella_salad", value: 14 },
];
mixingBowlRecipes.forEach((recipe) => {
  global.cooking.push({
    item: recipe.item,
    value: recipe.value * 3,
  });
});

global.fish = [
  { item: "aquaculture:atlantic_herring", value: 2 },
  { item: "minecraft:pufferfish", value: 2 },
  { item: "crabbersdelight:pufferfish_barrel", value: 14 },
  { item: "aquaculture:minnow", value: 2 },
  { item: "aquaculture:bluegill", value: 2 },
  { item: "aquaculture:perch", value: 2 },
  { item: "unusualfishmod:raw_sneep_snorp", value: 2 },
  { item: "minecraft:salmon", value: 2 },
  { item: "crabbersdelight:salmon_barrel", value: 22 },
  { item: "aquaculture:blackfish", value: 2 },
  { item: "unusualfishmod:raw_beaked_herring", value: 3 },
  { item: "aquaculture:brown_trout", value: 4 },
  { item: "aquaculture:carp", value: 4 },
  { item: "aquaculture:piranha", value: 5 },
  { item: "aquaculture:smallmouth_bass", value: 4 },
  { item: "minecraft:cod", value: 2 },
  { item: "unusualfishmod:raw_picklefish", value: 5 },
  { item: "crabbersdelight:cod_barrel", value: 43 },
  { item: "aquaculture:pollock", value: 6 },
  { item: "aquaculture:jellyfish", value: 6 },
  { item: "aquaculture:rainbow_trout", value: 6 },
  { item: "aquaculture:pink_salmon", value: 6 },
  { item: "minecraft:tropical_fish", value: 7 },
  { item: "crabbersdelight:tropical_fish_barrel", value: 65 },
  { item: "aquaculture:red_grouper", value: 8 },
  { item: "aquaculture:gar", value: 9 },
  { item: "aquaculture:muskellunge", value: 10 },
  { item: "unusualfishmod:raw_forkfish", value: 10 },
  { item: "unusualfishmod:raw_snowflake", value: 10 },
  { item: "aquaculture:synodontis", value: 11 },
  { item: "aquaculture:tambaqui", value: 11 },
  { item: "unusualfishmod:raw_sailor_barb", value: 11 },
  { item: "aquaculture:atlantic_cod", value: 12 },
  { item: "aquaculture:boulti", value: 13 },
  { item: "unusualfishmod:raw_aero_mono", value: 13 },
  { item: "aquaculture:leech", value: 14 },
  { item: "aquaculture:catfish", value: 14 },
  { item: "unusualfishmod:raw_bark_angelfish", value: 14 },
  { item: "unusualfishmod:raw_drooping_gourami", value: 18 },
  { item: "unusualfishmod:raw_demon_herring", value: 19 },
  { item: "aquaculture:tuna", value: 19 },
  { item: "unusualfishmod:raw_triple_twirl_pleco", value: 21 },
  { item: "aquaculture:bayad", value: 21 },
  { item: "aquaculture:arapaima", value: 22 },
  { item: "unusualfishmod:raw_blind_sailfin", value: 22 },
  { item: "aquaculture:atlantic_halibut", value: 23 },
  { item: "aquaculture:starshell_turtle", value: 24 },
  { item: "aquaculture:brown_shrooma", value: 26 },
  { item: "aquaculture:red_shrooma", value: 26 },
  { item: "aquaculture:arrau_turtle", value: 28 },
  { item: "unusualfishmod:raw_amber_goby", value: 29 },
  { item: "aquaculture:capitaine", value: 29 },
  { item: "aquaculture:box_turtle", value: 32 },
  { item: "unusualfishmod:raw_copperflame_anthias", value: 32 },
  { item: "unusualfishmod:raw_circus_fish", value: 32 },
  { item: "crittersandcompanions:koi_fish", value: 34 },
  { item: "unusualfishmod:raw_hatchetfish", value: 35 },
  { item: "unusualfishmod:raw_spindlefish", value: 37 },
  { item: "society:neptuna", value: 38 },
  { item: "aquaculture:pacific_halibut", value: 40 },
  { item: "unusualfishmod:raw_eyelash", value: 48 },
  { item: "unusualfishmod:raw_duality_damselfish", value: 45 },
  { item: "aquaculture:goldfish", value: 51 },
  // Crab Trap
  { item: "crabbersdelight:shrimp", value: 1 },
  { item: "crabbersdelight:shrimp_barrel", value: 4 },
  { item: "crabbersdelight:clawster", value: 2 },
  { item: "crabbersdelight:clawster_barrel", value: 14 },
  { item: "crabbersdelight:crab", value: 1 },
  { item: "crabbersdelight:crab_barrel", value: 7 },
  { item: "crabbersdelight:clam", value: 1 },
  { item: "crabbersdelight:clam_barrel", value: 4 },
  { item: "crabbersdelight:raw_clam_meat", value: 1 },
  // Nether
  { item: "netherdepthsupgrade:searing_cod", value: 26 },
  { item: "netherdepthsupgrade:blazefish", value: 41 },
  { item: "netherdepthsupgrade:lava_pufferfish", value: 26 },
  { item: "netherdepthsupgrade:obsidianfish", value: 51 },
  { item: "netherdepthsupgrade:bonefish", value: 36 },
  { item: "netherdepthsupgrade:wither_bonefish", value: 41 },
  { item: "netherdepthsupgrade:magmacubefish", value: 62 },
  { item: "netherdepthsupgrade:glowdine", value: 45 },
  { item: "netherdepthsupgrade:soulsucker", value: 31 },
  { item: "netherdepthsupgrade:fortress_grouper", value: 67 },
  { item: "netherdepthsupgrade:eyeball_fish", value: 41 },
];
// 54
global.smokedFish = [];
global.agedRoe = [];
global.fish.forEach((fish) => {
  const splitFish = fish.item.split(":");
  let fishId = splitFish[1];
  if (!["barrel", "roe", "meat"].some((denied) => fishId.includes(denied))) {
    if (fishId.includes("raw_")) {
      if (fishId === "raw_snowflake") fishId = "frosty_fin";
      else fishId = fishId.substring(4, fishId.length);
    }
    global.smokedFish.push({
      item: `society:smoked_${fishId}`,
      value: roundPrice(fish.value * 4),
    });
    global.agedRoe.push({
      item: `society:aged_${fishId}_roe`,
      value: roundPrice((Math.floor(fish.value / 3) + 16) * 15),
    });
  }
});

global.fish.forEach((fish) => {
  const splitFish = fish.item.split(":");
  let fishId = splitFish[1];
  if (!["barrel", "meat"].some((denied) => fish.item.includes(denied))) {
    if (fishId.includes("raw_")) {
      if (fishId === "raw_snowflake") fishId = "frosty_fin";
      else fishId = fishId.substring(4, fishId.length);
    }
    global.fish.push({
      item: `society:${fishId}_roe`,
      value: roundPrice(Math.floor(fish.value / 3) + 16),
    });
  }
});

global.miscAdventurer = [
  { item: "crittersandcompanions:clam", value: 51 },
  { item: "windswept:elder_feather", value: 13 },
  { item: "windswept:frozen_branch", value: 20 },
  { item: "crittersandcompanions:silk", value: 13 },
  { item: "society:river_jelly", value: 13 },
  { item: "society:ocean_jelly", value: 26 },
  { item: "society:nether_jelly", value: 51 },
  { item: "botania:black_lotus", value: 13 },
  { item: "automobility:dash_panel", value: 6 },
  { item: "create:experience_nugget", value: 1 },
  { item: "create:experience_block", value: 2 },
  { item: "society:gnome", value: 36 },
  { item: "minecraft:experience_bottle", value: 1 },
  { item: "create_enchantment_industry:hyper_experience_bottle", value: 55 },
  { item: "twigs:opaline_seashell", value: 2 },
  { item: "twigs:roseate_seashell", value: 2 },
  { item: "twigs:bronzed_seashell", value: 3 },
  { item: "twigs:tangerine_seashell", value: 2 },
  { item: "aquaculture:tin_can", value: 1 },
  { item: "simplehats:hatbag_common", value: 1 },
  { item: "simplehats:hatbag_uncommon", value: 1 },
  { item: "simplehats:hatbag_rare", value: 1 },
  { item: "simplehats:hatbag_epic", value: 1 },
  { item: "simplehats:hatbag_easter", value: 1 },
  { item: "simplehats:hatbag_summer", value: 1 },
  { item: "simplehats:hatbag_halloween", value: 1 },
  { item: "simplehats:hatbag_festive", value: 1 },
  { item: "minecraft:nautilus_shell", value: 6 },
  { item: "crabbersdelight:nautilus_shell_block", value: 64 },
  { item: "minecraft:echo_shard", value: 19 },
  { item: "minecraft:heart_of_the_sea", value: 26 },
  { item: "minecraft:nether_star", value: 205 },
  { item: "betterarcheology:artifact_shards", value: 6 },
  { item: "betterarcheology:unidentified_artifact", value: 58 },
  { item: "betterarcheology:sheep_fossil_head", value: 19 },
  { item: "betterarcheology:villager_fossil_head", value: 19 },
  { item: "betterarcheology:creeper_fossil_head", value: 19 },
  { item: "betterarcheology:ocelot_fossil_head", value: 19 },
  { item: "betterarcheology:wolf_fossil_head", value: 19 },
  { item: "betterarcheology:chicken_fossil_head", value: 19 },
  { item: "betterarcheology:guardian_fossil_head", value: 19 },
  { item: "betterarcheology:villager_fossil_body", value: 26 },
  { item: "betterarcheology:creeper_fossil_body", value: 26 },
  { item: "betterarcheology:ocelot_fossil_body", value: 26 },
  { item: "betterarcheology:wolf_fossil_body", value: 26 },
  { item: "betterarcheology:chicken_fossil_body", value: 26 },
  { item: "betterarcheology:guardian_fossil_body", value: 26 },
  { item: "betterarcheology:sheep_fossil_body", value: 26 },
  { item: "betterarcheology:creeper_fossil", value: 90 },
  { item: "betterarcheology:villager_fossil", value: 90 },
  { item: "betterarcheology:chicken_fossil", value: 90 },
  { item: "betterarcheology:ocelot_fossil", value: 90 },
  { item: "betterarcheology:wolf_fossil", value: 90 },
  { item: "betterarcheology:sheep_fossil", value: 90 },
  { item: "betterarcheology:guardian_fossil", value: 90 },
  { item: "supplementaries:antique_ink", value: 3 },
  { item: "paraglider:spirit_orb", value: 6 },
  { item: "minecraft:dragon_egg", value: 307 },
  { item: "society:furniture_box", value: 6 },
  { item: "crabbersdelight:pearl", value: 6 },
  { item: "crabbersdelight:pearl_block", value: 51 },
  { item: "trials:ominous_bottle", value: 6 },
  { item: "trials:trial_key", value: 11 },
  { item: "betterarcheology:vase_green", value: 11 },
  { item: "betterarcheology:vase", value: 13 },
  { item: "betterarcheology:vase_creeper", value: 14 },
  { item: "trials:trial_key_ominous", value: 22 },
  { item: "trials:heavy_core", value: 614 },
  { item: "trials:guster_pottery_sherd", value: 16 },
  { item: "trials:flow_pottery_sherd", value: 16 },
  { item: "trials:scrape_pottery_sherd", value: 16 },
  { item: "windswept:hoot_pottery_sherd", value: 18 },
  { item: "windswept:plumage_pottery_sherd", value: 18 },
  { item: "windswept:offshoot_pottery_sherd", value: 26 },
  { item: "windswept:flake_pottery_sherd", value: 26 },
  { item: "windswept:drupes_pottery_sherd", value: 26 },
  { item: "atmospheric:scythe_pottery_sherd", value: 51 },
  { item: "atmospheric:succulent_pottery_sherd", value: 51 },
  { item: "atmospheric:sun_pottery_sherd", value: 51 },
  { item: "minecraft:angler_pottery_sherd", value: 19 },
  { item: "minecraft:snort_pottery_sherd", value: 19 },
  { item: "minecraft:shelter_pottery_sherd", value: 19 },
  { item: "minecraft:archer_pottery_sherd", value: 8 },
  { item: "minecraft:skull_pottery_sherd", value: 8 },
  { item: "minecraft:miner_pottery_sherd", value: 8 },
  { item: "minecraft:prize_pottery_sherd", value: 8 },
  { item: "minecraft:brewer_pottery_sherd", value: 13 },
  { item: "minecraft:arms_up_pottery_sherd", value: 13 },
  { item: "minecraft:explorer_pottery_sherd", value: 19 },
  { item: "minecraft:blade_pottery_sherd", value: 19 },
  { item: "minecraft:mourner_pottery_sherd", value: 19 },
  { item: "minecraft:plenty_pottery_sherd", value: 19 },
  { item: "minecraft:sheaf_pottery_sherd", value: 8 },
  { item: "minecraft:burn_pottery_sherd", value: 8 },
  { item: "minecraft:danger_pottery_sherd", value: 8 },
  { item: "minecraft:friend_pottery_sherd", value: 8 },
  { item: "minecraft:heart_pottery_sherd", value: 8 },
  { item: "minecraft:heartbreak_pottery_sherd", value: 8 },
  { item: "minecraft:howl_pottery_sherd", value: 8 },
  { item: "minecraft:totem_of_undying", value: 45 },
  { item: "minecraft:dragon_head", value: 461 },
  { item: "quark:forgotten_hat", value: 11 },
  { item: "aquaculture:box", value: 3 },
  { item: "aquaculture:lockbox", value: 13 },
  { item: "aquaculture:treasure_chest", value: 51 },
  { item: "rottencreatures:treasure_chest", value: 410 },
  { item: "mysticaloaktree:wise_oak", value: 102 },
  { item: "minecraft:enchanted_golden_apple", value: 410 },
  { item: "minecraft:goat_horn", value: 51 },
  { item: "wildernature:bison_horn", value: 330 },
  { item: "botania:life_essence", value: 1000 },
  { item: "botania:manasteel_ingot", value: 3 },
  { item: "botania:manasteel_block", value: 29 },
  { item: "botania:mana_pearl", value: 10 },
  { item: "botania:mana_diamond", value: 113 },
  { item: "botania:mana_diamond_block", value: 1015 },
  { item: "botania:elementium_ingot", value: 10 },
  { item: "botania:elementium_block", value: 86 },
  { item: "botania:pixie_dust", value: 12 },
  { item: "botania:dragonstone", value: 140 },
  { item: "botania:dragonstone_block", value: 1260 },
  { item: "botania:terrasteel_ingot", value: 4410 },
  { item: "botania:terrasteel_block", value: 39686 },
  { item: "gamediscs:game_disc_flappy_bird", value: 49 },
  { item: "gamediscs:game_disc_slime", value: 100 },
  { item: "gamediscs:game_disc_rabbit", value: 410 },
  { item: "gamediscs:game_disc_blocktris", value: 1000 },
  { item: "gamediscs:game_disc_tnt_sweeper", value: 205 },
  { item: "gamediscs:game_disc_pong", value: 490 },
  { item: "gamediscs:game_disc_froggie", value: 320 },
];

global.plorts = [
  { type: "splendid_slimes:slimy", value: 64 },
  { type: "splendid_slimes:dusty", value: 64 },
  { type: "splendid_slimes:bony", value: 72 },
  { type: "splendid_slimes:rotting", value: 72 },
  { type: "splendid_slimes:mechanic", value: 90 },
  { type: "splendid_slimes:webby", value: 128 },
  { type: "splendid_slimes:luminous", value: 132 },
  { type: "splendid_slimes:juicy", value: 148 },
  { type: "splendid_slimes:puddle", value: 224 },
  { type: "splendid_slimes:boomcat", value: 256 },
  { type: "splendid_slimes:bear", value: 200 },
  { type: "splendid_slimes:all_seeing", value: 256 },
  { type: "splendid_slimes:bitwise", value: 288 },
  { type: "splendid_slimes:blazing", value: 256 },
  { type: "splendid_slimes:weeping", value: 320 },
  { type: "splendid_slimes:prisma", value: 400 },
  { type: "splendid_slimes:phantom", value: 512 },
  { type: "splendid_slimes:sweet", value: 768 },
  { type: "splendid_slimes:shulking", value: 1024 },
  { type: "splendid_slimes:ender", value: 1024 },
  { type: "splendid_slimes:orby", value: 1280 },
  { type: "splendid_slimes:minty", value: 1280 },
  { type: "splendid_slimes:sparkcat", value: 1400 },
  { type: "splendid_slimes:gold", value: 2048 },
];

global.slimeHearts = [];
global.plorts.forEach((plort) => {
  global.slimeHearts.push({
    type: plort.type,
    value: Math.floor(plort.value * 16),
  });
});

global.trades = new Map();
global.ore.forEach((oreItem) => {
  const { item, value } = oreItem;
  global.trades.set(item, {
    value: value,
    multiplier: "shippingbin:gem_sell_multiplier",
  });
});
global.pristine.forEach((pristineItem) => {
  const { item, value } = pristineItem;
  global.trades.set(item, {
    value: value,
    multiplier: "shippingbin:gem_sell_multiplier",
  });
});
global.crops.forEach((crop) => {
  const { item, value } = crop;
  global.trades.set(item, {
    value: value,
    multiplier: "shippingbin:crop_sell_multiplier",
  });
});
global.animalProducts.forEach((meat) => {
  const { item, value } = meat;
  global.trades.set(item, {
    value: value,
    multiplier: "shippingbin:crop_sell_multiplier",
  });
});
global.cooking.forEach((dish) => {
  const { item, value } = dish;
  global.trades.set(item, {
    value: value,
    multiplier: "shippingbin:crop_sell_multiplier",
  });
});
global.wines.forEach((wine) => {
  const { item, value } = wine;
  global.trades.set(item, {
    value: value,
    multiplier: "shippingbin:wood_sell_multiplier",
  });
});
global.brews.forEach((brew) => {
  const { item, value } = brew;
  global.trades.set(item, {
    value: value,
    multiplier: "shippingbin:wood_sell_multiplier",
  });
});
global.geodeList.forEach((treasure) => {
  const { item, value } = treasure;
  global.trades.set(item, {
    value: value,
    multiplier:
      item === "society:froggy_helm"
        ? "shippingbin:meat_sell_multiplier"
        : "shippingbin:gem_sell_multiplier",
  });
});
global.frozenGeodeList.forEach((treasure) => {
  const { item, value } = treasure;
  global.trades.set(item, {
    value: value,
    multiplier:
      item === "society:ribbit_drum"
        ? "shippingbin:meat_sell_multiplier"
        : "shippingbin:gem_sell_multiplier",
  });
});
global.magmaGeodeList.forEach((treasure) => {
  const { item, value } = treasure;
  global.trades.set(item, {
    value: value,
    multiplier:
      item === "society:ribbit_gadget"
        ? "shippingbin:meat_sell_multiplier"
        : "shippingbin:gem_sell_multiplier",
  });
});
global.gems.forEach((treasure) => {
  const { item, value } = treasure;
  global.trades.set(item, {
    value: value,
    multiplier: "shippingbin:gem_sell_multiplier",
  });
});
global.miscGeologist.forEach((treasure) => {
  const { item, value } = treasure;
  global.trades.set(item, {
    value: value,
    multiplier: "shippingbin:gem_sell_multiplier",
  });
});
global.artifacts.forEach((treasure) => {
  const { item, value } = treasure;
  global.trades.set(item, {
    value: value,
    multiplier: "shippingbin:meat_sell_multiplier",
  });
});
global.relics.forEach((treasure) => {
  const { item, value } = treasure;
  global.trades.set(item, {
    value: value,
    multiplier: "shippingbin:meat_sell_multiplier",
  });
});
global.preserves.forEach((jar) => {
  const { item, value } = jar;
  global.trades.set(item, {
    value: value,
    multiplier: "shippingbin:wood_sell_multiplier",
  });
});
global.dehydrated.forEach((dehydratee) => {
  const { item, value } = dehydratee;
  global.trades.set(item, {
    value: value,
    multiplier: "shippingbin:wood_sell_multiplier",
  });
});
global.artisanGoods.forEach((good) => {
  const { item, value } = good;
  global.trades.set(item, {
    value: value,
    multiplier: "shippingbin:wood_sell_multiplier",
  });
});
global.fish.forEach((fish) => {
  const { item, value } = fish;
  global.trades.set(item, {
    value: value,
    multiplier: "shippingbin:crop_sell_multiplier",
  });
});
global.smokedFish.forEach((fish) => {
  const { item, value } = fish;
  global.trades.set(item, {
    value: value,
    multiplier: "shippingbin:wood_sell_multiplier",
  });
});
global.agedRoe.forEach((fish) => {
  const { item, value } = fish;
  global.trades.set(item, {
    value: value,
    multiplier: "shippingbin:wood_sell_multiplier",
  });
});
global.cocktails.forEach((cocktail) => {
  const { item, value } = cocktail;
  global.trades.set(item, {
    value: value,
    multiplier: "shippingbin:crop_sell_multiplier",
  });
});
global.herbalBrews.forEach((brew) => {
  const { item, value } = brew;
  global.trades.set(item, {
    value: value,
    multiplier: "shippingbin:crop_sell_multiplier",
  });
});
global.logs.forEach((log) => {
  const { item, value } = log;
  global.trades.set(item, {
    value: value,
    multiplier: "shippingbin:crop_sell_multiplier",
  });
});
global.miscAdventurer.forEach((miscItem) => {
  const { item, value } = miscItem;
  global.trades.set(item, {
    value: value,
    multiplier: "shippingbin:meat_sell_multiplier",
  });
});
global.plorts.forEach((plort) => {
  const { type, value } = plort;
  global.trades.set(`splendid_slimes:plort/${type}`, {
    value: value,
    multiplier: "shippingbin:crop_sell_multiplier",
  });
});
global.slimeHearts.forEach((heart) => {
  const { type, value } = heart;
  global.trades.set(`splendid_slimes:slime_heart/${type}`, {
    value: value,
    multiplier: "shippingbin:crop_sell_multiplier",
  });
});
