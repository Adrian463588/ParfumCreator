import csv
import json
import os
import re
from collections import Counter

PACKAGING_KEYWORDS = [
    'kaleng kosong', 'botol kosong', 'botol parfum', 'jerigen', 'sumbu lilin',
    'candle wick', 'cetakan', 'mold', 'spatula', 'dropper', 'pipet', 'holder wood',
    'pot cream', 'spray bottle', 'kardus', 'plastik klip', 'bubble wrap',
    'centrifuge', 'corong', 'beaker glass', 'gelas ukur', 'silicone mold',
    'washing bottle', 'tutup botol', 'stearin', 'stearic acid', 'epsom salt'
]

CANDLE_KEYWORDS = [
    'cetakan lilin', 'sumbu lilin', 'candle wick', 'wood centering tool',
    'candle soap making', 'plaster pudding', 'wax sachet'
]

BUNDLE_KEYWORDS = [
    'all product', 'part 1', 'part 2', 'part 3', 'part 4',
    'sample size tester', 'p-r list', 's-u list', 'a-b list',
    'c-f list', 'g-l list', 'm-o list', 'v-z list', 'part list',
    'repack 50ml dan 100ml'
]

SOLVENT_KEYWORDS = [
    'alkohol absolute', 'alcohol absolute', 'ethanol 96%', 'pure solvent',
    'solvarome', 'aquadest', 'distilled water', 'sugarcane solvent',
    'perfumer solvent', 'denatured'
]

FIXATIVE_KEYWORDS = [
    'musk fixative', 'iso e super', 'ambroxan', 'galaxolide', 'fixatif', 'cashmeran', 'hedione'
]

BRAND_REGEXES = [
    ('LUZI', re.compile(r'\b(?:LUZI)\b', re.I)),
    ('PARFAROME', re.compile(r'\b(?:PARFAROME)\b', re.I)),
    ('MACRABAME', re.compile(r'\b(?:MACRABAME|MACBRAME)\b', re.I)),
    ('BENBERG', re.compile(r'\b(?:BENBERG\s*AROME|BENBERG)\b', re.I)),
    ('BELL', re.compile(r'\b(?:BELL)\b', re.I)),
    ('ELIE MOISE', re.compile(r'\b(?:ELIE\s+MOISE)\b', re.I)),
    ('SEKAWAN', re.compile(r'\b(?:SEKAWAN)\b', re.I)),
    ('EMSURE', re.compile(r'\b(?:EMSURE)\b', re.I)),
    ('MFA', re.compile(r'\b(?:MFA)\b', re.I)),
    ('EXPRESSIONS', re.compile(r'\b(?:EXPRESSIONS)\b', re.I)),
    ('ESSENTIALE', re.compile(r'\b(?:ESSENTIALE)\b', re.I)),
]

PACKAGING_SUFFIX_REGEX = re.compile(
    r'\b(?:segel\s+\d+\s*(?:kg|gr|g|ml|liter)|repack\s+\d+\s*(?:kg|gr|g|ml|liter)|kemasan\s+\d+\s*(?:kg|gr|g|ml|liter)|\d+\s*(?:gr|g|kg|ml|liter|pcs|cavity)|\"part\s*\d+\"|part\s*\d+)\b',
    re.I
)

INSPIRED_BY_REGEX = re.compile(
    r'^(.*?)\s+(?:by|product\s+by|for\s+women|for\s+men|for\s+women\s+and\s+men)\b',
    re.I
)

def classify_row(name, brand):
    name_lower = name.lower()

    if any(k in name_lower for k in CANDLE_KEYWORDS):
        return 'CANDLE_SUPPLY'
    if any(k in name_lower for k in PACKAGING_KEYWORDS):
        return 'PACKAGING'
    if any(k in name_lower for k in BUNDLE_KEYWORDS) and ('all product' in name_lower or 'list' in name_lower):
        return 'BUNDLE_LISTING'
    if any(k in name_lower for k in SOLVENT_KEYWORDS):
        return 'SOLVENT'
    if any(k in name_lower for k in FIXATIVE_KEYWORDS):
        return 'FIXATIVE'
    if 'essential oil' in name_lower or 'minyak atsiri' in name_lower or 'pure isolate' in name_lower or 'absolute' in name_lower:
        return 'NATURAL_MATERIAL'
    if brand in ['LUZI', 'PARFAROME', 'MACRABAME', 'BENBERG', 'BELL', 'EXPRESSIONS'] or 'fragrance oil' in name_lower or 'parfum' in name_lower or 'perfume' in name_lower:
        return 'FRAGRANCE_OIL'

    return 'UNKNOWN'

def clean_product_name(raw_name):
    # Remove obvious packaging suffixes
    cleaned = PACKAGING_SUFFIX_REGEX.sub('', raw_name)
    cleaned = re.sub(r'\s+', ' ', cleaned).strip(' ,"\'')
    # Remove trailing/leading punctuation
    cleaned = re.sub(r'^[,\-\s]+|[,\-\s]+$', '', cleaned)
    return cleaned

def extract_inspired_by(cleaned_name, brand):
    match = re.search(r'^(.*?)(?:\s+(?:FOR\s+WOMEN|FOR\s+MEN|FOR\s+WOMEN\s+AND\s+MEN|By\s+' + (brand or 'PARFAROME') + r'|Product\s+By\s+' + (brand or 'PARFAROME') + r'))', cleaned_name, re.I)
    if match:
        inspired = match.group(1).strip(' ,"\'')
        if len(inspired) >= 2 and not inspired.lower().startswith('all product'):
            return inspired
    return None

def process_datasets():
    base_dir = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    db_dir = os.path.join(base_dir, 'docs', 'Database')
    output_dir = os.path.join(base_dir, 'app', 'src', 'main', 'assets', 'data')
    os.makedirs(output_dir, exist_ok=True)

    files = [
        os.path.join(db_dir, 'fragrance_oils_scraped.csv'),
        os.path.join(db_dir, 'raw_materials_scraped.csv')
    ]

    stats = {
        'total_rows': 0,
        'accepted_ingredients': 0,
        'excluded_rows': 0,
        'by_category': Counter(),
        'by_brand': Counter(),
        'duplicates_merged': 0
    }

    natural_list = []
    luzi_list = []
    parfarome_list = []
    macrabame_list = []
    other_fragrance_list = []

    seen_keys = set()

    for filepath in files:
        if not os.path.exists(filepath):
            print(f'Warning: {filepath} not found')
            continue

        with open(filepath, 'r', encoding='utf-8-sig', errors='ignore') as f:
            reader = csv.DictReader(f)
            for row in reader:
                stats['total_rows'] += 1
                raw_name = row.get('name', '').strip()
                itemid = row.get('itemid', '').strip()
                shopid = row.get('shopid', '').strip()

                if not raw_name:
                    stats['excluded_rows'] += 1
                    continue

                # Brand detection
                detected_brand = None
                for b_name, b_pat in BRAND_REGEXES:
                    if b_pat.search(raw_name):
                        detected_brand = b_name
                        break

                category = classify_row(raw_name, detected_brand)
                stats['by_category'][category] += 1

                # Filter out packaging, candle supplies, and bundle listings
                if category in ['PACKAGING', 'CANDLE_SUPPLY', 'BUNDLE_LISTING', 'UNKNOWN']:
                    stats['excluded_rows'] += 1
                    continue

                # Clean display name
                display_name = clean_product_name(raw_name)
                if not display_name or len(display_name) < 2:
                    stats['excluded_rows'] += 1
                    continue

                # Deduplication key based on normalized name + brand
                dedup_key = (display_name.lower(), detected_brand or 'UNKNOWN')
                if dedup_key in seen_keys:
                    stats['duplicates_merged'] += 1
                    continue
                seen_keys.add(dedup_key)

                # Extract inspired-by if applicable
                inspired_by = extract_inspired_by(raw_name, detected_brand)

                # Determine note type & family if detectable from name or default to UNKNOWN / Not Available
                # We do NOT hallucinate CAS, IFRA, or chemistry data!
                # Density default for synthetic fragrance oils: 0.95 g/ml, for natural/solvent: 1.0 g/ml
                is_synthetic = category in ['FRAGRANCE_OIL', 'FIXATIVE', 'SOLVENT'] or (detected_brand in ['LUZI', 'PARFAROME', 'MACRABAME', 'BENBERG', 'BELL'])

                # Check for standard natural types
                note_type = "MIDDLE" # documented default
                name_lower = display_name.lower()
                if any(x in name_lower for x in ['bergamot', 'lemon', 'citrus', 'orange', 'grapefruit', 'mandarin', 'lime', 'mint', 'eucalyptus']):
                    fragrance_family = "CITRUS"
                    note_type = "TOP"
                elif any(x in name_lower for x in ['rose', 'jasmine', 'ylang', 'lavender', 'floral', 'melati', 'geranium', 'violet', 'lotus', 'tuberose', 'lily']):
                    fragrance_family = "FLORAL"
                    note_type = "HEART"
                elif any(x in name_lower for x in ['sandalwood', 'cedar', 'oud', 'woody', 'patchouli', 'vetiver', 'gaharu', 'cendana', 'oakmoss', 'pine']):
                    fragrance_family = "WOODY"
                    note_type = "BASE"
                elif any(x in name_lower for x in ['vanilla', 'musk', 'amber', 'oriental', 'benzoin', 'labdanum', 'myrrh', 'resins', 'spicy', 'cinnamon', 'clove']):
                    fragrance_family = "ORIENTAL"
                    note_type = "BASE"
                elif any(x in name_lower for x in ['sea', 'ocean', 'aqua', 'water', 'breeze', 'fresh']):
                    fragrance_family = "FRESH"
                    note_type = "TOP"
                else:
                    fragrance_family = "FRESH" if not is_synthetic else "ORIENTAL"

                density = 0.95 if is_synthetic else 1.0

                item_obj = {
                    "itemId": itemid,
                    "shopId": shopid,
                    "name": display_name,
                    "rawName": raw_name,
                    "type": "SYNTHETIC" if is_synthetic else "NATURAL",
                    "brand": detected_brand,
                    "noteType": note_type,
                    "fragranceFamily": fragrance_family,
                    "densityGml": density,
                    "casNumber": None,
                    "inspiredBy": inspired_by,
                    "description": f"{display_name} ({detected_brand or 'Generic'})",
                    "sourcePlant": None,
                    "ifraLimit": None,
                    "isCustom": False
                }

                stats['accepted_ingredients'] += 1
                if detected_brand:
                    stats['by_brand'][detected_brand] += 1

                if not is_synthetic:
                    natural_list.append(item_obj)
                elif detected_brand == 'LUZI':
                    luzi_list.append(item_obj)
                elif detected_brand == 'PARFAROME':
                    parfarome_list.append(item_obj)
                elif detected_brand == 'MACRABAME':
                    macrabame_list.append(item_obj)
                else:
                    other_fragrance_list.append(item_obj)

    # Combine natural essential oils with baseline canonical oils if needed
    canonical_naturals = [
        {"itemId": "NAT-001", "name": "Bergamot Essential Oil", "type": "NATURAL", "brand": None, "noteType": "TOP", "fragranceFamily": "CITRUS", "densityGml": 0.882, "casNumber": "8007-75-8", "inspiredBy": None, "description": "Citrusy, fresh, slightly floral with herbal undertones", "sourcePlant": "Citrus bergamia", "ifraLimit": 0.4, "isCustom": False},
        {"itemId": "NAT-002", "name": "Lavender Essential Oil (40/42)", "type": "NATURAL", "brand": None, "noteType": "HEART", "fragranceFamily": "FLORAL", "densityGml": 0.885, "casNumber": "8000-28-0", "inspiredBy": None, "description": "Fresh, herbaceous, sweet floral aroma", "sourcePlant": "Lavandula angustifolia", "ifraLimit": None, "isCustom": False},
        {"itemId": "NAT-003", "name": "Sandalwood Essential Oil", "type": "NATURAL", "brand": None, "noteType": "BASE", "fragranceFamily": "WOODY", "densityGml": 0.970, "casNumber": "8006-87-9", "inspiredBy": None, "description": "Warm, precious woody, smooth and balsamic", "sourcePlant": "Santalum album", "ifraLimit": None, "isCustom": False},
        {"itemId": "NAT-004", "name": "Patchouli Essential Oil (Dark)", "type": "NATURAL", "brand": None, "noteType": "BASE", "fragranceFamily": "WOODY", "densityGml": 0.960, "casNumber": "8014-09-3", "inspiredBy": None, "description": "Earthy, woody, rich and deep aged aroma", "sourcePlant": "Pogostemon cablin", "ifraLimit": None, "isCustom": False},
        {"itemId": "NAT-005", "name": "Rose Absolute", "type": "NATURAL", "brand": None, "noteType": "HEART", "fragranceFamily": "FLORAL", "densityGml": 0.980, "casNumber": "8007-01-0", "inspiredBy": None, "description": "Deep, rich, sweet honeyed floral scent", "sourcePlant": "Rosa damascena", "ifraLimit": 0.25, "isCustom": False},
        {"itemId": "NAT-006", "name": "Jasmine Sambac Absolute", "type": "NATURAL", "brand": None, "noteType": "HEART", "fragranceFamily": "FLORAL", "densityGml": 0.975, "casNumber": "8022-96-6", "inspiredBy": None, "description": "Intense, warm, indolic white floral bouquet", "sourcePlant": "Jasminum sambac", "ifraLimit": 0.7, "isCustom": False},
        {"itemId": "NAT-007", "name": "Vetiver Essential Oil (Java)", "type": "NATURAL", "brand": None, "noteType": "BASE", "fragranceFamily": "WOODY", "densityGml": 0.990, "casNumber": "8016-96-4", "inspiredBy": None, "description": "Smoky, woody, earthy root aroma from Java", "sourcePlant": "Chrysopogon zizanioides", "ifraLimit": None, "isCustom": False},
        {"itemId": "NAT-008", "name": "Oud / Agarwood Essential Oil", "type": "NATURAL", "brand": None, "noteType": "BASE", "fragranceFamily": "WOODY", "densityGml": 0.985, "casNumber": "94350-01-3", "inspiredBy": None, "description": "Deep, complex, resinous, balsamic and animalic", "sourcePlant": "Aquilaria malaccensis", "ifraLimit": None, "isCustom": False},
        {"itemId": "NAT-009", "name": "Lemon Essential Oil (Cold Pressed)", "type": "NATURAL", "brand": None, "noteType": "TOP", "fragranceFamily": "CITRUS", "densityGml": 0.852, "casNumber": "8008-56-8", "inspiredBy": None, "description": "Zesty, bright, fresh sparkling citrus rind", "sourcePlant": "Citrus limon", "ifraLimit": 2.0, "isCustom": False},
        {"itemId": "NAT-010", "name": "Vanilla Planifolia Absolute", "type": "NATURAL", "brand": None, "noteType": "BASE", "fragranceFamily": "ORIENTAL", "densityGml": 1.050, "casNumber": "8024-06-4", "inspiredBy": None, "description": "Warm, rich, sweet, balsamic creamy vanilla", "sourcePlant": "Vanilla planifolia", "ifraLimit": None, "isCustom": False},
        {"itemId": "NAT-011", "name": "Cedarwood Atlas Oil", "type": "NATURAL", "brand": None, "noteType": "BASE", "fragranceFamily": "WOODY", "densityGml": 0.935, "casNumber": "8023-85-6", "inspiredBy": None, "description": "Dry woody, balsamic, slightly sweet and pencil-shavings", "sourcePlant": "Cedrus atlantica", "ifraLimit": None, "isCustom": False},
        {"itemId": "NAT-012", "name": "Ylang Ylang Extra Oil", "type": "NATURAL", "brand": None, "noteType": "HEART", "fragranceFamily": "FLORAL", "densityGml": 0.940, "casNumber": "8006-81-3", "inspiredBy": None, "description": "Intensely sweet, floral, exotic and banana-fruity", "sourcePlant": "Cananga odorata", "ifraLimit": 0.8, "isCustom": False}
    ]

    all_naturals = canonical_naturals + natural_list

    # Save to JSON files
    with open(os.path.join(output_dir, 'ingredients_natural.json'), 'w', encoding='utf-8') as f:
        json.dump({"version": 1, "ingredients": all_naturals}, f, indent=2, ensure_ascii=False)

    with open(os.path.join(output_dir, 'ingredients_luzi.json'), 'w', encoding='utf-8') as f:
        json.dump({"version": 1, "ingredients": luzi_list}, f, indent=2, ensure_ascii=False)

    with open(os.path.join(output_dir, 'ingredients_parfarome.json'), 'w', encoding='utf-8') as f:
        json.dump({"version": 1, "ingredients": parfarome_list + other_fragrance_list}, f, indent=2, ensure_ascii=False)

    with open(os.path.join(output_dir, 'ingredients_macrabame.json'), 'w', encoding='utf-8') as f:
        json.dump({"version": 1, "ingredients": macrabame_list}, f, indent=2, ensure_ascii=False)

    print("=" * 60)
    print("DATASET INGESTION & NORMALIZATION REPORT")
    print("=" * 60)
    print(f"Total Rows Processed:        {stats['total_rows']}")
    print(f"Accepted Ingredient Rows:    {stats['accepted_ingredients']}")
    print(f"Excluded Noise/Packaging:    {stats['excluded_rows']}")
    print(f"Duplicates Merged:           {stats['duplicates_merged']}")
    print("\nCategorization Breakdown:")
    for cat, count in stats['by_category'].most_common():
        print(f"  - {cat:20s}: {count}")
    print("\nDetected Brands Breakdown (Accepted):")
    for b, count in stats['by_brand'].most_common():
        print(f"  - {b:20s}: {count}")
    print("\nGenerated Bundles in assets/data/:")
    print(f"  - ingredients_natural.json:    {len(all_naturals)} items")
    print(f"  - ingredients_luzi.json:       {len(luzi_list)} items")
    print(f"  - ingredients_parfarome.json:  {len(parfarome_list + other_fragrance_list)} items")
    print(f"  - ingredients_macrabame.json:  {len(macrabame_list)} items")
    print("=" * 60)

if __name__ == '__main__':
    process_datasets()
