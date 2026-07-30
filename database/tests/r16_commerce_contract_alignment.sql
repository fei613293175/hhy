BEGIN;
SET search_path TO hhy, public;

DO $$
DECLARE
  product_id bigint;
BEGIN
  INSERT INTO hhy.products(product_code,type,name,status,version)
  VALUES ('R16-V047-PRODUCT','APP','V047合同商品','ACTIVE',0)
  RETURNING id INTO product_id;

  INSERT INTO hhy.product_skus(
    product_id,code,price_cent,duration,attributes_json,status,
    name,duration_days,benefits_json,version
  ) VALUES (
    product_id,'R16-V047-VALID',0,0,
    jsonb_build_object(
      'name','V047合法SKU',
      'benefits',jsonb_build_array(jsonb_build_object(
        'benefitCode','B1','name',repeat('名',255),'value',0,'unit',repeat('U',64)
      ))
    ),
    'ACTIVE','V047合法SKU',0,
    jsonb_build_array(jsonb_build_object(
      'benefitCode','B1','name',repeat('名',255),'value',0,'unit',repeat('U',64)
    )),
    0
  );

  BEGIN
    INSERT INTO hhy.product_skus(
      product_id,code,price_cent,duration,attributes_json,status,
      name,duration_days,benefits_json,version
    ) VALUES (
      product_id,'R16-V047-NAME-OVER',0,1,
      jsonb_build_object(
        'name','越界SKU',
        'benefits',jsonb_build_array(jsonb_build_object(
          'benefitCode','B2','name',repeat('N',256),'value',1
        ))
      ),
      'ACTIVE','越界SKU',1,
      jsonb_build_array(jsonb_build_object(
        'benefitCode','B2','name',repeat('N',256),'value',1
      )),
      0
    );
    RAISE EXCEPTION 'R16_V047_NAME_256_WAS_ACCEPTED';
  EXCEPTION WHEN check_violation THEN NULL;
  END;

  BEGIN
    INSERT INTO hhy.product_skus(
      product_id,code,price_cent,duration,attributes_json,status,
      name,duration_days,benefits_json,version
    ) VALUES (
      product_id,'R16-V047-UNIT-OVER',0,1,
      jsonb_build_object(
        'name','越界SKU',
        'benefits',jsonb_build_array(jsonb_build_object(
          'benefitCode','B3','name','权益','value',1,'unit',repeat('U',65)
        ))
      ),
      'ACTIVE','越界SKU',1,
      jsonb_build_array(jsonb_build_object(
        'benefitCode','B3','name','权益','value',1,'unit',repeat('U',65)
      )),
      0
    );
    RAISE EXCEPTION 'R16_V047_UNIT_65_WAS_ACCEPTED';
  EXCEPTION WHEN check_violation THEN NULL;
  END;

  BEGIN
    INSERT INTO hhy.product_skus(
      product_id,code,price_cent,duration,attributes_json,status,
      name,duration_days,benefits_json,version
    ) VALUES (
      product_id,'R16-V047-NEGATIVE',0,-1,
      '{"name":"负时长","benefits":[]}','ACTIVE','负时长',-1,'[]',0
    );
    RAISE EXCEPTION 'R16_V047_NEGATIVE_DURATION_WAS_ACCEPTED';
  EXCEPTION WHEN check_violation THEN NULL;
  END;

  IF hhy.r16_benefits_valid(
      '[{"benefitCode":"B4","name":"权益","value":1,"extra":true}]'::jsonb) THEN
    RAISE EXCEPTION 'R16_V047_EXTRA_BENEFIT_FIELD_WAS_ACCEPTED';
  END IF;
  IF hhy.r16_benefits_valid('{"benefitCode":"B5"}'::jsonb) THEN
    RAISE EXCEPTION 'R16_V047_NON_ARRAY_BENEFITS_WAS_ACCEPTED';
  END IF;
END;
$$;

ROLLBACK;
SELECT 'R16_COMMERCE_CONTRACT_ALIGNMENT PASS' AS result;
