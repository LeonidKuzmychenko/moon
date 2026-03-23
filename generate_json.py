import json
import random
import os

def generate():
    tiles_dir = r'c:\IntelijIdeaProjects\test-parsing\test-moon\src\main\resources\tiles'
    tiles = [f for f in os.listdir(tiles_dir) if f.endswith(('.jpg', '.png', '.jpeg'))]
    
    total_areas = 9
    num_users = 3
    
    # Each user gets total_areas / num_users groups
    groups_per_user = total_areas // num_users
    extra_groups = total_areas % num_users
    
    users = []
    current_area_id = 1
    
    for i in range(1, num_users + 1):
        user_groups = []
        num_groups = groups_per_user + (1 if i <= extra_groups else 0)
        
        for j in range(1, num_groups + 1):
            user_groups.append({
                "groupId": j,
                "url": "https://google.com",
                "tile": random.choice(tiles),
                "areaIds": [current_area_id]
            })
            current_area_id += 1
            
        users.append({
            "userId": i,
            "groups": user_groups
        })
        
    data = {"users": users}
    
    with open(r'c:\IntelijIdeaProjects\test-parsing\test-moon\src\main\resources\user\userAreas.json', 'w', encoding='utf-8') as f:
        json.dump(data, f, ensure_ascii=False, indent=2)

if __name__ == '__main__':
    generate()
