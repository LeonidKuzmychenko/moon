package lk.tech.testmoon.service;

import lk.tech.testmoon.config.AppPathsProperties;
import lk.tech.testmoon.model.*;
import lk.tech.testmoon.repository.SphereRepository;
import lk.tech.testmoon.repository.UserAreaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class SphereService {

    private final SphereRepository sphereRepository;
    private final UserAreaRepository userAreaRepository;
    private final AppPathsProperties properties;

    public SphereData generateSphere() {
        double radius = properties.getSphereRadius();
        int latSegments = properties.getSphereLatSegments();
        int lonSegments = properties.getSphereLonSegments();

        validateInput(radius, latSegments, lonSegments);

        List<SphereArea> areas = new ArrayList<>();
        int areaIdCounter = 1;

        double dPhi = Math.PI / latSegments;
        double dTheta = (2 * Math.PI) / lonSegments;

        for (int lat = 0; lat < latSegments; lat++) {
            for (int lon = 0; lon < lonSegments; lon++) {
                double phiStart = lat * dPhi;
                double phiEnd = (lat + 1) * dPhi;

                double thetaStart = lon * dTheta;
                double thetaEnd = (lon + 1) * dTheta;

                List<SphereVertex> vertices = new ArrayList<>();
                List<SphereUv> uvs = new ArrayList<>();
                List<Integer> triangles;
                String type;

                // Верхний полюс
                if (lat == 0) {
                    vertices.add(createVertex(radius, phiEnd, thetaStart));
                    vertices.add(createVertex(radius, phiEnd, thetaEnd));
                    vertices.add(createVertex(radius, phiStart, thetaStart)); // north pole

                    uvs.add(new SphereUv(0.0, 1.0));
                    uvs.add(new SphereUv(1.0, 1.0));
                    uvs.add(new SphereUv(0.5, 0.0));

                    triangles = List.of(0, 2, 1);
                    type = "TRIANGLE";
                }
                // Нижний полюс
                else if (lat == latSegments - 1) {
                    vertices.add(createVertex(radius, phiStart, thetaStart));
                    vertices.add(createVertex(radius, phiStart, thetaEnd));
                    vertices.add(createVertex(radius, phiEnd, thetaStart)); // south pole

                    uvs.add(new SphereUv(0.0, 1.0));
                    uvs.add(new SphereUv(1.0, 1.0));
                    uvs.add(new SphereUv(0.5, 0.0));

                    triangles = List.of(0, 1, 2);
                    type = "TRIANGLE";
                }
                // Средние ряды
                else {
                    vertices.add(createVertex(radius, phiStart, thetaStart)); // 0
                    vertices.add(createVertex(radius, phiEnd, thetaStart));   // 1
                    vertices.add(createVertex(radius, phiEnd, thetaEnd));     // 2
                    vertices.add(createVertex(radius, phiStart, thetaEnd));   // 3

                    uvs.add(new SphereUv(0.0, 1.0)); // 0
                    uvs.add(new SphereUv(0.0, 0.0)); // 1
                    uvs.add(new SphereUv(1.0, 0.0)); // 2
                    uvs.add(new SphereUv(1.0, 1.0)); // 3

                    triangles = List.of(0, 2, 1, 0, 3, 2);
                    type = "QUAD";
                }

                areas.add(SphereArea.builder()
                        .areaId(areaIdCounter++)
                        .type(type)
                        .vertices(vertices)
                        .uv(uvs)
                        .triangles(triangles)
                        .build());
            }
        }

        SphereData sphereData = SphereData.builder()
                .radius(radius)
                .latSegments(latSegments)
                .lonSegments(lonSegments)
                .areas(areas)
                .build();

        sphereRepository.save(sphereData);
        return sphereData;
    }

    private void validateInput(double radius, int latSegments, int lonSegments) {
        if (radius <= 0) {
            throw new IllegalArgumentException("radius must be > 0");
        }
        if (latSegments < 2) {
            throw new IllegalArgumentException("latSegments must be >= 2");
        }
        if (lonSegments < 3) {
            throw new IllegalArgumentException("lonSegments must be >= 3");
        }
    }

    private SphereVertex createVertex(double radius, double phi, double theta) {
        double x = radius * Math.sin(phi) * Math.cos(theta);
        double y = radius * Math.cos(phi);
        double z = radius * Math.sin(phi) * Math.sin(theta);

        return SphereVertex.builder()
                .x(x)
                .y(y)
                .z(z)
                .build();
    }

    public SphereData getSphereData() {
        SphereData sphere = sphereRepository.find();
        if (sphere == null) {
            throw new IllegalStateException("sphere.json not found");
        }

        UserAreaConfig userConfig = userAreaRepository.findAll();

        Map<Integer, UserGroupInfo> areaMap = new HashMap<>();
        if (userConfig != null && userConfig.getUsers() != null) {
            for (User user : userConfig.getUsers()) {
                if (user.getGroups() == null) {
                    continue;
                }
                for (UserGroup group : user.getGroups()) {
                    if (group.getAreaIds() == null) {
                        continue;
                    }
                    for (Integer areaId : group.getAreaIds()) {
                        if (areaMap.containsKey(areaId)) {
                            log.warn("AreaId {} is assigned to multiple groups. Using the first match.", areaId);
                            continue;
                        }
                        areaMap.put(areaId, new UserGroupInfo(user.getUserId(), group.getGroupId(), group.getUrl()));
                    }
                }
            }
        }

        for (SphereArea area : sphere.getAreas()) {
            UserGroupInfo info = areaMap.get(area.getAreaId());

            if (info == null) {
                throw new IllegalStateException(
                        "Area " + area.getAreaId() + " has no group mapping"
                );
            }

            area.setUserId(info.userId);
            area.setGroupId(info.groupId);
            area.setUrl(info.url);
        }

        return sphere;
    }

    private record UserGroupInfo(Long userId, Long groupId, String url) {}
}