UPDATE unique_ids SET current_id = 10000 WHERE index_type = 'general';

INSERT INTO binaries VALUES
                     ('23a716cbd2f862a1de9e1a431c28cc62bb64d582','6c2109657d94ae5147bf02eaf9e834b8',2026,'82dcb03e24f3a2169747f4d29c168336f08b3fe6ddfa1b9ce5f83ce9aca2777d'),
                     ('c98418c4057468039e877bb7f66f56c2b6894d22','2273cccf8533764d0cf1b385ddcd20bb',2024,'3e042d0bdd94a70d620bd3e1619727c81aaaeb608ef086a57ea7dd3d35e268c5'),
                     ('ea9eecf2a8e35a28060d6f32dda4ac13cc4fc67d','efd8134d3d3961f66366317f63b1cd45',2026,'b1b8cc71cb928f97e3a28cb43c30641fd75ed17191f6789890820171d563a35e'),
                     ('119cc4d32a53f9cdd6d9677fc3b23af33109eb19','1607e99969645422021f6c83b3148a3a',932761,'1130d17f4f9c36055ad6e74c9218d890e2cfe4777436d89242a0191c28debddb'),
                     ('7c9d0afa50a66aa77273d30b4b2535b376d266a5','9c1ebbcc7116588cebf19fe5d9bc9458',994795,'b87989cc37873c0af4bb7e652b0043c16828c9b3347243f481f48d0e64d67069'),
                     ('23a716cbd2f862a1de9e1a431c28cc62bb64d581','6c2109657d94ae5147bf02eaf9e834b9',2026,'82dcb03e24f3a2169747f4d29c168336f08b3fe6ddfa1b9ce5f83ce9aca2777e');

INSERT INTO nodes VALUES
                   (2333253,1,'nuget-local','.','shay-1-test.24.1.0-prerelease.nupkg',1,1709108809148,'admin',1709109860015,'admin',1709109860081,2026,'23a716cbd2f862a1de9e1a431c28cc62bb64d582','23a716cbd2f862a1de9e1a431c28cc62bb64d582','6c2109657d94ae5147bf02eaf9e834b8','6c2109657d94ae5147bf02eaf9e834b8','82dcb03e24f3a2169747f4d29c168336f08b3fe6ddfa1b9ce5f83ce9aca2777d','a055ac99bca83c4ac96e276090f43a47c5e0c3b5'),
                   (2333269,1,'nuget-local','.','shay-1-test.25.1.0.nupkg',1,1709108809240,'admin',1709109860052,'admin',1709109860121,2026,'ea9eecf2a8e35a28060d6f32dda4ac13cc4fc67d','ea9eecf2a8e35a28060d6f32dda4ac13cc4fc67d','efd8134d3d3961f66366317f63b1cd45','efd8134d3d3961f66366317f63b1cd45','b1b8cc71cb928f97e3a28cb43c30641fd75ed17191f6789890820171d563a35e','54570052c10f3f9e410c6ea475ce40ca84cec115'),
                   (2333349,1,'nuget-local','.','shay-1-test.3.1.0.nupkg',1,1709108809727,'admin',1709109860013,'admin',1709109860080,2024,'c98418c4057468039e877bb7f66f56c2b6894d22','c98418c4057468039e877bb7f66f56c2b6894d22','2273cccf8533764d0cf1b385ddcd20bb','2273cccf8533764d0cf1b385ddcd20bb','3e042d0bdd94a70d620bd3e1619727c81aaaeb608ef086a57ea7dd3d35e268c5','9a0133f688e4370db0cefd64eaa921bf813f39be'),
                   (8005,1,'nuget-local','.','azure.core.1.37.0.nupkg',1,1707899826189,'admin',1707899826105,'admin',1707899826190,932761,'119cc4d32a53f9cdd6d9677fc3b23af33109eb19','NO_ORIG','1607e99969645422021f6c83b3148a3a','NO_ORIG','1130d17f4f9c36055ad6e74c9218d890e2cfe4777436d89242a0191c28debddb','5a368151b55858ba81d019bdae9f0ff9efb9bce7'),
                   (4362002,1,'nuget-local','.','zstdsharp.port.0.7.3.nupkg',1,1709469713098,'admin',1709469710968,'admin',1709469713104,994795,'7c9d0afa50a66aa77273d30b4b2535b376d266a5','NO_ORIG','9c1ebbcc7116588cebf19fe5d9bc9458','NO_ORIG','b87989cc37873c0af4bb7e652b0043c16828c9b3347243f481f48d0e64d67069','f99de949ad2ca76a6c2189b6cdc4bedbbbd8cd33'),
                   (4001,0,'nuget-local','.','.',0,1718463780936,null,1718463780936,null,1718463780936,0,null,null,null,null,null,'83e938ac9e09c68d00f74445ea6b3f67f6a37701'),
                   (5001,1,'nuget-local','.','shay-1-t.26.0.0.nupkg',1,1718463780937,'admin',1718463780937,'admin',1718463780937,2026,'23a716cbd2f862a1de9e1a431c28cc62bb64d581','23a716cbd2f862a1de9e1a431c28cc62bb64d581','6c2109657d94ae5147bf02eaf9e834b9','6c2109657d94ae5147bf02eaf9e834b9','82dcb03e24f3a2169747f4d29c168336f08b3fe6ddfa1b9ce5f83ce9aca2777e','a055ac99bca83c4ac96e276090f43a47c5e0c3b1');

INSERT INTO node_props VALUES
                           (2333256,2333253,'nuget.version','24.1.0-prerelease'),
                           (2333257,2333253,'nuget.dependency','SampleDependency:1.0'),
                           (2333267,2333253,'nuget.dependency','AnotherDependency:2.0'),
                           (2333258,2333253,'nuget.authors','Support'),
                           (2333259,2333253,'nuget.copyright','Copyright 2016'),
                           (2333260,2333253,'nuget.owners','Support'),
                           (2333261,2333253,'nuget.id','shay-1-test'),
                           (2333262,2333253,'nuget.requireLicenseAcceptance','false'),
                           (2333263,2333253,'nuget.digest','1M+QxIjHH9eMlzYFEf3BVAf7HrfOyX6ykp787vKVq5d3QxLxToP0i0FmP/E6HCSR8wu0LSFvecJeQHyCQ0cbzQ=='),
                           (2333264,2333253,'nuget.tags','Tag1 Tag2'),
                           (2333265,2333253,'nuget.description','bla bla'),
                           (2333266,2333253,'nuget.releaseNotes','Summary of changes made in this release of the package.'),
                           (2333268,2333253,'nuget.raw.version','24.1.0-prerelease+271024-020017-f4bafg'),

                           (2333272,2333269,'nuget.version','25.1.0'),
                           (2333273,2333269,'nuget.dependency','SampleDependency:1.0:'),
                           (2333283,2333269,'nuget.dependency','AnotherDependency:2.0'),
                           (2333274,2333269,'nuget.authors','Support'),
                           (2333275,2333269,'nuget.copyright','Copyright 2016'),
                           (2333276,2333269,'nuget.owners','Support'),
                           (2333277,2333269,'nuget.id','shay-1-test'),
                           (2333278,2333269,'nuget.requireLicenseAcceptance','false'),
                           (2333279,2333269,'nuget.digest','oWOjOGp+XtSgXi7m+Dgf2f89Xa9a/Incl/+LFl4drJP+O4KOKMMnbhXUnClD+jr4fsrsTUJCWQq3rtNqOZQI9A=='),
                           (2333280,2333269,'nuget.tags','Tag1 Tag2'),
                           (2333281,2333269,'nuget.description','bla bla'),
                           (2333282,2333269,'nuget.releaseNotes','Summary of changes made in this release of the package.'),
                           (2333284,2333269,'nuget.raw.version','25.1.0+281024-020017-f4bafg'),

                           (2333352,2333349,'nuget.version','3.1.0'),
                           (2333357,2333349,'nuget.id','shay-1-test'),
                           (2333358,2333349,'nuget.raw.version','3.1.0'),


                           (8115,8005,'nuget.id','Azure.Core'),
                           (8113,8005,'nuget.version','1.37.0'),
                           (8109,8005,'nuget.tags','Microsoft Azure Client Pipeline'),
                           (8095,8005,'nuget.title','this is the title'),
                           (8099,8005,'nuget.authors','Microsoft'),
                           (8111,8005,'nuget.description','This is the implementation of the Azure Client Pipeline'),
                           (8019,8005,'nuget.summary','summary of azure'),
                           (8081,8005,'nuget.owners','alex is the owner'),

                           (4362018,4362002,'nuget.id','ZstdSharp.Port'),
                           (4362005,4362002,'nuget.version','0.7.3'),

                           (5002,5001,'nuget.version','26.0.0'),
                           (5003,5001,'nuget.id','shay-1-t'),
                           (5004,5001,'nuget.raw.version','1.0.0');

-- Setting download count for azure.core.1.37.0.nupkg
INSERT INTO stats VALUES (8005, 11, 1707899826190, 'admin');






