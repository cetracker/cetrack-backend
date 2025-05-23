INSERT INTO BIKE (id, manufacturer, model, bought_at, created_at) VALUES
      ('70556a9d-bf01-42eb-b882-b5938fff7023', 'Vaust', 'Fragola', '2006-06-15 14:00:00', LOCALTIMESTAMP()),
      ('99a133db-4f9b-4a5b-b6f9-8a5ec96d9db7', 'Cycle', 'TT', '2010-08-15 12:00:00', LOCALTIMESTAMP());

INSERT INTO PART (id, name, created_at, bought_at) VALUES
      ('ca68d5a1-cb85-4e4a-a719-4aa380b76325', 'Rimebreak Campa Veloce front', LOCALTIMESTAMP(), null),
      ('f8d98a1d-ab33-4df1-b6c5-eefdf4aa3dca', 'Rimbreak Campa Veloce rear', LOCALTIMESTAMP(), null),
      ('f351b44c-a49b-4871-bd0f-50f7af790b8c', 'Derailleur Campa Chorus front', LOCALTIMESTAMP(), null),
      ('d33a6e96-eb63-4279-8a0c-521b8a4f25d4', 'Derailleur Campa Veloce rear', LOCALTIMESTAMP(), null),
      ('4bbf00a2-7539-4d5f-944f-aa524aa1323a', 'Sprocket 12:24 10x', LOCALTIMESTAMP(), null),
      ('75efe2d4-bc85-430b-af26-1deb58409b43', 'Chain Veloce', LOCALTIMESTAMP(), null),
      ('9355e25b-78b4-44b3-9a13-7597657f988c', 'Tire Schalbe 23mm front orig', LOCALTIMESTAMP(), null),
      ('e7781636-a761-40b4-b588-c26925a071ec', 'Tire Schalbe 23mm rear orig', LOCALTIMESTAMP(), null),
      ('d972d4e5-1c73-4c8e-be50-354c9742c22a', 'Tire Conti GP4000S 25mm front', LOCALTIMESTAMP(), null),
      ('4a634d19-0c24-4f4e-a262-04b5e3251913', 'Tire Conti GP4000S 25mm rear', LOCALTIMESTAMP(), null),
      ('8d8e2e59-645d-4228-a007-cd17e5d62149', 'Front Wheel DT-Swiss', LOCALTIMESTAMP(), null),
      ('5cb5afb9-3e1a-4f10-8082-6d72b7b27335', 'Rear Wheel DT-SWiss', LOCALTIMESTAMP(), null),
      ('dcbdcfc6-8c0c-4677-a5e4-61b4dc01f526', 'Front Wheel Campa Zonda', LOCALTIMESTAMP(), null),
      ('80bbc300-5475-4932-8296-c00aaeaa9e5f', 'Rear Wheel Campa Zonda', LOCALTIMESTAMP(), null),
      ('5fce029f-1e0d-47d0-b220-e604cafc5451', 'Saddle Selle Italia', LOCALTIMESTAMP(), null),
      ('35300fd6-751c-42eb-8632-30ce2646958c', 'TT Front Wheel', LOCALTIMESTAMP(), null);

INSERT INTO PART_TYPE(id, bike_id, mandatory, name, created_at) VALUES
      ('82eabc08-6397-46fd-8581-98d9f18a83ff', '99a133db-4f9b-4a5b-b6f9-8a5ec96d9db7', '1', 'Front Wheel', LOCALTIMESTAMP()),
      ('c1c2c16b-1890-43ad-8aee-f8e5c9f68928', '99a133db-4f9b-4a5b-b6f9-8a5ec96d9db7', '1', 'Saddle', LOCALTIMESTAMP()),
      ('6296981d-92e4-45d3-b26c-331a58284e21', '70556a9d-bf01-42eb-b882-b5938fff7023', '1', 'Chain', LOCALTIMESTAMP()),
      ('59143611-6a57-4e92-8fc6-de7e1a434625', '70556a9d-bf01-42eb-b882-b5938fff7023', '1', 'Saddle', LOCALTIMESTAMP()),
      ('821f1d71-2515-47d7-b771-2badb3ffa2f9', '70556a9d-bf01-42eb-b882-b5938fff7023', '1', 'Front Derailleur', LOCALTIMESTAMP()),
      ('33905c08-a045-4e43-aefa-2c2963ffe582', '70556a9d-bf01-42eb-b882-b5938fff7023', '1', 'Rear Derailleur', LOCALTIMESTAMP()),
      ('12b6f43b-af8f-4d2d-8ae4-2d5a98c825a6', '70556a9d-bf01-42eb-b882-b5938fff7023', '1', 'Sprocket', LOCALTIMESTAMP()),
      ('6dd6d551-9449-4766-8efb-ddab90812702', '70556a9d-bf01-42eb-b882-b5938fff7023', '1', 'Front Wheel', LOCALTIMESTAMP()),
      ('3d7ffc2f-ff86-4697-bbb1-86298cdd7e37', '70556a9d-bf01-42eb-b882-b5938fff7023', '1', 'Rear Wheel', LOCALTIMESTAMP()),
      ('3f403402-4a49-4b8e-b35a-d9a365615fc4', '70556a9d-bf01-42eb-b882-b5938fff7023', '1', 'Front Tire', LOCALTIMESTAMP()),
      ('3d5576df-756b-465c-998d-a8f166540d35', '70556a9d-bf01-42eb-b882-b5938fff7023', '1', 'Rear Tire', LOCALTIMESTAMP()),
      ('c47e51e6-0f07-4b4b-8390-9b065e59e197', '70556a9d-bf01-42eb-b882-b5938fff7023', '1', 'Front Rimbrake', LOCALTIMESTAMP()),
      ('24996c8c-c559-45a2-8e4a-116f1a8f33ae', '70556a9d-bf01-42eb-b882-b5938fff7023', '1', 'Rear Rimbrake', LOCALTIMESTAMP());

INSERT INTO PART_PART_TYPES(part_id, part_type_id, valid_from, valid_until) VALUES
      ('5fce029f-1e0d-47d0-b220-e604cafc5451', 'c1c2c16b-1890-43ad-8aee-f8e5c9f68928', '2011-08-10 12:00:00', null ),
      ('35300fd6-751c-42eb-8632-30ce2646958c', '82eabc08-6397-46fd-8581-98d9f18a83ff', '2010-08-15 12:00:00', null ),
      ('75efe2d4-bc85-430b-af26-1deb58409b43', '6296981d-92e4-45d3-b26c-331a58284e21', '2006-06-15 14:00:00', null ),
      ('5fce029f-1e0d-47d0-b220-e604cafc5451', '59143611-6a57-4e92-8fc6-de7e1a434625', '2006-06-15 13:59:59', '2011-08-09 23:59:59' ),
      ('ca68d5a1-cb85-4e4a-a719-4aa380b76325', 'c47e51e6-0f07-4b4b-8390-9b065e59e197', '2006-06-15 13:59:59', null ),
      ('f8d98a1d-ab33-4df1-b6c5-eefdf4aa3dca', '24996c8c-c559-45a2-8e4a-116f1a8f33ae', '2006-06-15 13:59:59', null ),
      ('4bbf00a2-7539-4d5f-944f-aa524aa1323a', '12b6f43b-af8f-4d2d-8ae4-2d5a98c825a6', '2006-06-15 13:59:59', null ),
      ('f351b44c-a49b-4871-bd0f-50f7af790b8c', '821f1d71-2515-47d7-b771-2badb3ffa2f9', '2006-06-15 13:59:59', null ),
      ('8d8e2e59-645d-4228-a007-cd17e5d62149', '6dd6d551-9449-4766-8efb-ddab90812702', '2006-06-15 13:59:59', '2008-06-06 23:59:59' ),
      ('dcbdcfc6-8c0c-4677-a5e4-61b4dc01f526', '6dd6d551-9449-4766-8efb-ddab90812702', '2008-06-07 13:59:59', '2008-10-10 23:59:59' ),
      ('8d8e2e59-645d-4228-a007-cd17e5d62149', '6dd6d551-9449-4766-8efb-ddab90812702', '2008-10-11 00:09:09', '2009-05-31 23:59:59' ),
      ('dcbdcfc6-8c0c-4677-a5e4-61b4dc01f526', '6dd6d551-9449-4766-8efb-ddab90812702', '2009-06-01 00:00:00', '2009-10-10 23:59:59' ),
      ('8d8e2e59-645d-4228-a007-cd17e5d62149', '6dd6d551-9449-4766-8efb-ddab90812702', '2009-10-11 00:09:09', '2010-06-06 23:59:59' ),
      ('dcbdcfc6-8c0c-4677-a5e4-61b4dc01f526', '6dd6d551-9449-4766-8efb-ddab90812702', '2010-06-07 13:59:59', null ),
      ('5cb5afb9-3e1a-4f10-8082-6d72b7b27335', '3d7ffc2f-ff86-4697-bbb1-86298cdd7e37', '2006-06-15 13:59:59', '2010-06-06 23:59:59' ),
      ('80bbc300-5475-4932-8296-c00aaeaa9e5f', '3d7ffc2f-ff86-4697-bbb1-86298cdd7e37', '2010-06-07 00:00:00', '2010-08-31 23:59:59' ),
      ('5cb5afb9-3e1a-4f10-8082-6d72b7b27335', '3d7ffc2f-ff86-4697-bbb1-86298cdd7e37', '2010-09-01 00:00:00', null ),
      ('e7781636-a761-40b4-b588-c26925a071ec', '3d5576df-756b-465c-998d-a8f166540d35', '2006-06-15 14:00:00', '2010-08-05 23:59:59' ),
      ('4a634d19-0c24-4f4e-a262-04b5e3251913', '3d5576df-756b-465c-998d-a8f166540d35', '2010-08-06 14:00:00', null ),
      ('9355e25b-78b4-44b3-9a13-7597657f988c', '3f403402-4a49-4b8e-b35a-d9a365615fc4', '2006-06-15 14:00:00', '2010-08-05 23:59:59' ),
      ('d972d4e5-1c73-4c8e-be50-354c9742c22a', '3f403402-4a49-4b8e-b35a-d9a365615fc4', '2010-08-06 00:00:00', null ),
      ('d33a6e96-eb63-4279-8a0c-521b8a4f25d4', '33905c08-a045-4e43-aefa-2c2963ffe582', '2006-06-15 14:00:00', null );


INSERT INTO TOUR (id, title, start_year, start_month, start_day, started_at, distance, duration_moving, alt_up, alt_down, power_total, bike_id, created_at) VALUES
      ('8e449d84-5373-4285-9f65-bcd49f86c67e', 'Passo Giau',  '2010', '06', '07','2010-06-07 10:22:46', 53120, 7614, '192', '192', '5043', '70556a9d-bf01-42eb-b882-b5938fff7023', LOCALTIMESTAMP()),
      ('2bc77a21-e0b7-4600-a5fa-385b0a5c5e21', 'Tourmalido',  '2010', '06', '11','2010-06-11 09:50:08', 43160, 5534, '192', '192', '5043', '70556a9d-bf01-42eb-b882-b5938fff7023', LOCALTIMESTAMP()),
      ('f1701850-953e-42e9-868a-dd3ccbf9bd53', 'Demo Tour 01','2010', '06', '26','2010-06-26 07:45:24', 93203, 12520, '192', '192', '5043', '70556a9d-bf01-42eb-b882-b5938fff7023', LOCALTIMESTAMP()),
      ('bd40eae5-9aab-4d7a-a426-5f39e98556b6', 'Demo Tour 02','2010', '07', '01','2010-07-01 09:46:06', 103002, 13237, '192', '192', '5043', '70556a9d-bf01-42eb-b882-b5938fff7023', LOCALTIMESTAMP()),
      ('cbb66578-de2b-4ad9-a742-3ce28e311e4a', 'Demo Tour 03','2010', '07', '06','2010-07-06 10:31:00', 14405, 514, '192', '192', '5043', '70556a9d-bf01-42eb-b882-b5938fff7023', LOCALTIMESTAMP()),
      ('eeb847ea-6cd2-4c76-8755-5aa426bd1113', 'Demo Tour 04','2010', '07', '07','2010-07-07 07:51:39', 78320, 9553, '192', '192', '5043', '70556a9d-bf01-42eb-b882-b5938fff7023', LOCALTIMESTAMP()),
      ('00e916f7-cb39-4f33-b53b-094a2163466b', 'Demo Tour 05','2010', '07', '08','2010-07-08 07:58:30', 104080, 6548, '192', '192', '5043', '70556a9d-bf01-42eb-b882-b5938fff7023', LOCALTIMESTAMP()),
      ('da0f0f4d-9990-4f2e-b711-c12b1a61a8ed', 'Demo Tour 06','2010', '07', '09','2010-07-09 08:57:57', 46280, 9045, '192', '192', '5043', '70556a9d-bf01-42eb-b882-b5938fff7023', LOCALTIMESTAMP()),
      ('80c5ee5b-6bab-4f0a-99f5-a8685f1bbf4e', 'Demo Tour 07','2010', '07', '10','2010-07-10 10:41:35', 15650, 1679, '192', '192', '5043', '70556a9d-bf01-42eb-b882-b5938fff7023', LOCALTIMESTAMP()),
      ('3e9ef8b4-0e0b-4cbc-9698-579e15a8ad27', 'Demo Tour 08','2010', '07', '11','2010-07-11 07:31:36', 63560, 12140, '192', '192', '5043', '70556a9d-bf01-42eb-b882-b5938fff7023', LOCALTIMESTAMP()),
      ('ded9f370-1c86-43b1-8720-d825a4eea084', 'Demo Tour 09','2010', '08', '13','2010-08-13 16:39:32', 41705, 6457, '192', '192', '5043', '70556a9d-bf01-42eb-b882-b5938fff7023', LOCALTIMESTAMP()),
      ('eb97f6a4-25f5-40db-9eba-ccea366eb434', 'Demo Tour 10','2010', '08', '15','2010-08-15 07:17:27', 45170, 7333, '192', '192', '5043', '70556a9d-bf01-42eb-b882-b5938fff7023', LOCALTIMESTAMP()),
      ('4131bf4b-5682-4d89-a478-6304fade9c8d', 'Demo Tour 11','2010', '08', '20','2010-08-20 07:34:54', 67590, 9063, '192', '192', '5043', '70556a9d-bf01-42eb-b882-b5938fff7023', LOCALTIMESTAMP()),
      ('dd1f0ae0-ea89-4208-b2bf-68e0e113dab0', 'Demo Tour 12','2010', '08', '21','2010-08-21 16:13:40', 114890, 19513, '192', '192', '5043', '70556a9d-bf01-42eb-b882-b5938fff7023', LOCALTIMESTAMP()),
      ('ee735d93-428b-413e-b6dd-6f618143e5de', 'Demo Tour 13','2010', '08', '22','2010-08-22 07:05:16', 93170, 16334, '192', '192', '5043', '70556a9d-bf01-42eb-b882-b5938fff7023', LOCALTIMESTAMP()),
      ('dc2f3403-6865-48e3-a6fc-a26e5ecfd730', 'Demo Tour 14','2010', '08', '25','2010-08-25 07:26:40', 40150, 9806, '192', '192', '5043', '70556a9d-bf01-42eb-b882-b5938fff7023', LOCALTIMESTAMP()),
      ('ae9deeec-0b85-429b-a159-b370926bc2ce', 'Demo Tour 15','2010', '09', '01','2010-09-01 11:37:03', 68280, 13275, '192', '192', '5043', '70556a9d-bf01-42eb-b882-b5938fff7023', LOCALTIMESTAMP()),
      ('abb21049-bd53-4a07-b987-4bc846a90006', 'Demo Tour 16','2010', '09', '05','2010-09-05 07:19:35', 95300, 16761, '192', '192', '5043', '70556a9d-bf01-42eb-b882-b5938fff7023', LOCALTIMESTAMP()),
      ('0139b0e7-ce8b-4e17-88b1-2055bcb64f68', 'Demo Tour 17','2010', '09', '13','2010-09-13 20:00:22', 76310, 5886, '192', '192', '5043', '70556a9d-bf01-42eb-b882-b5938fff7023', LOCALTIMESTAMP()),
      ('938d40d8-ab27-414f-a8d8-9f3706121338', 'Demo Tour 18','2010', '09', '15','2010-09-15 09:31:01', 48420, 9050, '192', '192', '5043', '70556a9d-bf01-42eb-b882-b5938fff7023', LOCALTIMESTAMP()),
      ('e3f3865a-576c-4cfb-8623-697df4240d03', 'Demo Tour 19','2010', '09', '15','2010-09-15 13:39:23', 91400, 16004, '192', '192', '5043', '70556a9d-bf01-42eb-b882-b5938fff7023', LOCALTIMESTAMP()),
      ('589f010e-b1ea-4550-85e5-071a4dc1171b', 'Demo Tour 21','2010', '09', '17','2010-09-17 07:14:52', 53430, 5966, '192', '192', '5043', '70556a9d-bf01-42eb-b882-b5938fff7023', LOCALTIMESTAMP()),
      ('66a9e33d-7654-4ef0-9067-faf944a7a95f', 'Demo Tour 22','2010', '09', '19','2010-09-19 10:07:52', 78350, 3877, '192', '192', '5043', '70556a9d-bf01-42eb-b882-b5938fff7023', LOCALTIMESTAMP()),
      ('5ed033fa-605b-4422-a30b-c736a86aa422', 'Demo Tour 23','2010', '09', '20','2010-09-20 12:15:55', 69280, 7247, '192', '192', '5043', '70556a9d-bf01-42eb-b882-b5938fff7023', LOCALTIMESTAMP()),
      ('1e59bf9b-dc52-4ca1-a941-19370412d71b', 'Demo Tour 24','2010', '09', '23','2010-09-23 10:49:48', 41360, 5205, '192', '192', '5043', '70556a9d-bf01-42eb-b882-b5938fff7023', LOCALTIMESTAMP()),
      ('a9b49c73-b5b8-4842-9736-21881e9d7062', 'Demo Tour 25','2010', '09', '30','2010-09-30 10:39:20', 45170, 5517, '192', '192', '5043', '70556a9d-bf01-42eb-b882-b5938fff7023', LOCALTIMESTAMP()),
      ('f27a93b0-491c-4cc7-b7ef-75c6c2967912', 'Demo Tour 26','2010', '10', '02','2010-10-02 12:29:07', 60390, 7842, '192', '192', '5043', '70556a9d-bf01-42eb-b882-b5938fff7023', LOCALTIMESTAMP()),
      ('ac0cfd0a-f000-4685-8004-ae0624bcb9f6', 'Demo Tour 27','2010', '10', '05','2010-10-05 07:17:17', 81240, 9318, '192', '192', '5043', '70556a9d-bf01-42eb-b882-b5938fff7023', LOCALTIMESTAMP()),
      ('6cbc1de9-79f1-4d50-978a-8ebc33174fb2', 'Demo Tour 11-01', '2011', '03', '05', '2011-03-05 13:47:07', 43108, 3900, '192', '192', '5043', '70556a9d-bf01-42eb-b882-b5938fff7023', LOCALTIMESTAMP()),
      ('4dd224e8-e168-4124-94da-c12ed949c789', 'Demo Tour 11-02', '2011', '04', '05', '2011-04-05 13:47:07', 54280, 5945, '192', '192', '5043', '70556a9d-bf01-42eb-b882-b5938fff7023', LOCALTIMESTAMP()),
      ('fe1b57da-2132-46dd-b1fc-cb7787be9675', 'Demo Tour 11-03', '2011', '05', '05', '2011-05-05 13:47:07', 65318, 7290, '192', '192', '5043', '70556a9d-bf01-42eb-b882-b5938fff7023', LOCALTIMESTAMP()),
      ('81bfadaa-67bb-485e-8cf6-a420d81885b4', 'Demo Tour 11-04', '2011', '06', '05', '2011-06-05 13:47:07', 76438, 9234, '192', '192', '5043', '70556a9d-bf01-42eb-b882-b5938fff7023', LOCALTIMESTAMP()),
      ('c7d2d0d8-8af8-44d5-b073-c91ea0b99fc0', 'Demo Tour 11-05', '2011', '07', '05', '2011-07-05 13:47:07', 87835, 9999, '192', '192', '5043', '70556a9d-bf01-42eb-b882-b5938fff7023', LOCALTIMESTAMP()),
      ('d10d8724-0d12-431c-813d-5a93328428e3', 'Demo TT-Tour 11-01', '2011', '08', '25', '2011-08-25 11:17:23', 10000, 8999, '150', '150', '444', '99a133db-4f9b-4a5b-b6f9-8a5ec96d9db7', LOCALTIMESTAMP());
