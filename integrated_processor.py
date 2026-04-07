import os
import io
import json
import logging
import math
import shutil
import time
import re
from PIL import Image, ImageChops
from PIL.ExifTags import TAGS, GPSTAGS
import exifread
from datetime import datetime
import numpy as np
from ultralytics import YOLO
import requests

# 配置日志
logging.basicConfig(level=logging.INFO, format='%(asctime)s - %(levelname)s - %(message)s')
logger = logging.getLogger(__name__)

class IntegratedImageProcessor:
    def __init__(self, target_ratio=16, api_url="http://localhost:3001/api/image-data", angle_correction=0):
        """
        整合的影像处理器
        结合YOLO目标检测和元数据提取功能
        
        参数：
        - target_ratio: 压缩目标比例
        - api_url: API上传地址
        - angle_correction: 角度校正值（度），用于微调旋转角度
                          正数表示需要额外逆时针旋转，负数表示需要额外顺时针旋转
        """
        self.target_ratio = target_ratio
        self.supported_formats = ['.jpg', '.jpeg', '.JPG', '.JPEG', '.png', '.PNG']
        self.api_url = os.getenv("IMAGE_API_URL", api_url)
        self.device_token = os.getenv("IMAGE_DEVICE_TOKEN", "")
        self.angle_correction = angle_correction  # 角度校正值
        
        # 初始化YOLO模型
        self.yolo_model = None
        self.init_yolo_model()
        
        # 目标检测类别映射
        self.class_names = {
            0: "nest",           # 鸟巢
            1: "insulator_defect",  # 异常绝缘子
            2: "balloon"         # 气球
        }
    
    def init_yolo_model(self):
        """初始化YOLO模型"""
        try:
            model_path = "/home/nvidia/lcy/YOLOv8-det/workspace/yolo11/best.onnx"
            if os.path.exists(model_path):
                self.yolo_model = YOLO(model_path, task='detect')
                logger.info(f"YOLO模型加载成功: {model_path}")
            else:
                logger.warning(f"YOLO模型文件不存在: {model_path}")
        except Exception as e:
            logger.error(f"YOLO模型初始化失败: {e}")
    
    def should_swap_dimensions(self, orientation):
        """判断是否需要交换宽高"""
        return orientation >= 5 and orientation <= 8
        
    def extract_exif_metadata(self, image_path):
        """提取EXIF和XMP元数据信息"""
        metadata = {}
        
        try:
            # 使用exifread读取详细EXIF信息
            with open(image_path, 'rb') as f:
                tags = exifread.process_file(f, details=True)
            
            # 解析GPS坐标
            gps_latitude = None
            gps_longitude = None
            absolute_altitude = None
            
            if 'GPS GPSLatitude' in tags and 'GPS GPSLatitudeRef' in tags:
                lat_ref = str(tags['GPS GPSLatitudeRef'])
                lat_values = tags['GPS GPSLatitude'].values
                if len(lat_values) >= 3:
                    degrees = float(lat_values[0])
                    minutes = float(lat_values[1])
                    seconds = float(lat_values[2])
                    gps_latitude = degrees + minutes/60 + seconds/3600
                    if lat_ref == 'S':
                        gps_latitude = -gps_latitude
            
            if 'GPS GPSLongitude' in tags and 'GPS GPSLongitudeRef' in tags:
                lon_ref = str(tags['GPS GPSLongitudeRef'])
                lon_values = tags['GPS GPSLongitude'].values
                if len(lon_values) >= 3:
                    degrees = float(lon_values[0])
                    minutes = float(lon_values[1])
                    seconds = float(lon_values[2])
                    gps_longitude = degrees + minutes/60 + seconds/3600
                    if lon_ref == 'W':
                        gps_longitude = -gps_longitude
            
            if 'GPS GPSAltitude' in tags:
                absolute_altitude = float(tags['GPS GPSAltitude'].values[0])
            
            # 获取图像尺寸
            width = None
            height = None
            if 'EXIF ExifImageWidth' in tags:
                width = int(tags['EXIF ExifImageWidth'].values[0])
            if 'EXIF ExifImageLength' in tags:
                height = int(tags['EXIF ExifImageLength'].values[0])
            
            if width is None and 'Image ImageWidth' in tags:
                width = int(tags['Image ImageWidth'].values[0])
            if height is None and 'Image ImageLength' in tags:
                height = int(tags['Image ImageLength'].values[0])
            
            # 获取焦距
            focal_length = None
            if 'EXIF FocalLengthIn35mmFilm' in tags:
                focal_length = int(tags['EXIF FocalLengthIn35mmFilm'].values[0])
            
            # 获取方向
            orientation = 1
            if 'Image Orientation' in tags:
                orientation_str = str(tags['Image Orientation'])
                if 'Rotated 90 CW' in orientation_str:
                    orientation = 6
                elif 'Rotated 180' in orientation_str:
                    orientation = 3
                elif 'Rotated 90 CCW' in orientation_str:
                    orientation = 8
                else:
                    try:
                        orientation = int(tags['Image Orientation'].values[0])
                    except:
                        orientation = 1
            
            # 检查是否需要交换宽高
            if self.should_swap_dimensions(orientation) and width and height:
                width, height = height, width
                logger.info(f"根据方向值 {orientation}，交换了宽高: {width}x{height}")
            
            # 获取拍摄时间
            create_date = None
            modify_date = None
            if 'EXIF DateTimeOriginal' in tags:
                create_date = str(tags['EXIF DateTimeOriginal'])
            if 'Image DateTime' in tags:
                modify_date = str(tags['Image DateTime'])
            
            # 构建元数据字典
            metadata = {
                # GPS信息
                "GPSLatitude": gps_latitude,
                "GPSLongitude": gps_longitude,
                "AbsoluteAltitude": absolute_altitude,
                "RelativeAltitude": None,
                
                # 飞行器姿态信息
                "FlightYawDegree": None,
                "FlightRollDegree": None,
                "FlightPitchDegree": None,
                
                # 云台姿态信息
                "GimbalRollDegree": None,
                "GimbalYawDegree": None,
                "GimbalPitchDegree": None,
                
                # 飞行速度信息
                "FlightXSpeed": None,
                "FlightYSpeed": None,
                "FlightZSpeed": None,
                
                # 时间信息
                "CreateDate": create_date,
                "ModifyDate": modify_date,
                
                # 图像信息
                "width": width,
                "height": height,
                "focalLength": focal_length,
                "orientation": orientation,
                
                # 相机信息
                "Make": str(tags.get('Image Make', '')),
                "Model": str(tags.get('Image Model', '')),
                "Software": str(tags.get('Image Software', '')),
                "BodySerialNumber": str(tags.get('EXIF BodySerialNumber', '')),
                
                # 拍摄参数
                "ExposureTime": str(tags.get('EXIF ExposureTime', '')),
                "FNumber": str(tags.get('EXIF FNumber', '')),
                "ISOSpeedRatings": str(tags.get('EXIF ISOSpeedRatings', '')),
                "FocalLength": str(tags.get('EXIF FocalLength', ''))
            }
            
            # 尝试从XMP数据中提取额外信息
            try:
                self._extract_xmp_data(image_path, metadata)
            except Exception as e:
                logger.warning(f"无法提取XMP数据: {e}")
            
        except Exception as e:
            logger.error(f"提取EXIF元数据失败 {image_path}: {e}")
            
        return metadata
    
    def _extract_xmp_data(self, image_path, metadata):
        """从XMP数据中提取大疆飞行器和云台信息"""
        try:
            dji_tags = [
                "AbsoluteAltitude", "RelativeAltitude", 
                "GimbalRollDegree", "GimbalYawDegree", "GimbalPitchDegree",
                "FlightRollDegree", "FlightYawDegree", "FlightPitchDegree",
                "FlightXSpeed", "FlightYSpeed", "FlightZSpeed"
            ]
            
            with open(image_path, 'rb') as f:
                data = f.read()
            
            xmp_start = data.find(b'<x:xmpmeta')
            xmp_end = data.find(b'</x:xmpmeta')
            
            if xmp_start != -1 and xmp_end != -1:
                xmp_bytes = data[xmp_start:xmp_end+12]
                xmp_str = xmp_bytes.decode('utf-8', errors='ignore')
                
                found_tags = []
                for tag in dji_tags:
                    try:
                        pattern1 = f'drone-dji:{tag}="([^"]*)"'
                        pattern2 = f'{tag}="([^"]*)"'
                        pattern3 = f'<drone-dji:{tag}>([^<]*)</drone-dji:{tag}>'
                        
                        import re
                        match1 = re.search(pattern1, xmp_str)
                        match2 = re.search(pattern2, xmp_str)
                        match3 = re.search(pattern3, xmp_str)
                        
                        value = None
                        if match1:
                            value = match1.group(1)
                        elif match2:
                            value = match2.group(1)
                        elif match3:
                            value = match3.group(1)
                        
                        if value is not None and value.strip():
                            try:
                                numeric_value = float(value)
                                metadata[tag] = numeric_value
                                found_tags.append(f"{tag}={numeric_value}")
                            except ValueError:
                                metadata[tag] = value.strip()
                                found_tags.append(f"{tag}={value}")
                                
                    except Exception as e:
                        logger.warning(f"解析XMP标签 {tag} 失败: {e}")
                        continue
                
                logger.info(f"成功提取XMP数据，找到 {len(found_tags)} 个DJI标签")
                
        except Exception as e:
            logger.error(f"XMP数据提取失败: {e}")
    
    def calculate_rotation_angle(self, flight_yaw, gimbal_yaw, orientation):
        """
        计算图像旋转到正北向所需的角度
        
        参数说明：
        - flight_yaw: 飞行器偏航角（0°=正北，90°=正东，顺时针）
        - gimbal_yaw: 云台偏航角（相对于飞行器的角度）
        - orientation: EXIF方向值
        
        返回：
        - 逆时针旋转角度（PIL rotate使用逆时针）
        - 如果拍摄方向是顺时针45°，返回-45°（即315°），使图像转到正北
        """
        flight_yaw = flight_yaw if flight_yaw is not None else 0
        gimbal_yaw = gimbal_yaw if gimbal_yaw is not None else 0
        
        # 计算总偏航角（拍摄时的方向，顺时针，0°=正北）
        total_yaw = (flight_yaw + gimbal_yaw) % 360
        
        # 将顺时针角度转换为逆时针角度
        # 如果拍摄方向是顺时针45°（指向东北），要转到正北，需要逆时针旋转-45°（即顺时针旋转45°）
        # PIL的rotate是逆时针，所以需要取负值
        rotation_angle = -total_yaw % 360
        
        # 根据EXIF方向值调整
        adjusted_angle = self.adjust_by_exif_orientation(rotation_angle, orientation)
        
        # 应用角度校正（用于微调）
        final_angle = (adjusted_angle + self.angle_correction) % 360
        
        logger.info(f"角度计算: 飞行器偏航={flight_yaw}°, 云台偏航={gimbal_yaw}°, 方向={orientation}")
        logger.info(f"计算结果: 总偏航（顺时针）={total_yaw:.1f}°, 旋转角度（逆时针）={rotation_angle:.1f}°, EXIF调整后={adjusted_angle:.1f}°, 校正后={final_angle:.1f}°")
        
        return final_angle
    
    def adjust_by_exif_orientation(self, calculated_angle, exif_orientation):
        """
        根据EXIF方向调整计算出的角度
        
        EXIF方向值说明：
        - 1: 正常（0°）
        - 3: 旋转180°
        - 6: 顺时针旋转90°（需要逆时针旋转-90°来纠正）
        - 8: 逆时针旋转90°（需要顺时针旋转90°，即逆时针旋转-90°来纠正）
        
        注意：EXIF方向值表示图像已经旋转的方向，我们需要补偿这个旋转
        """
        result = calculated_angle
        
        if exif_orientation == 6:
            # 图像已经顺时针旋转90°，需要逆时针旋转-90°来纠正
            # 但calculated_angle已经是逆时针角度，所以需要减去90°
            result = calculated_angle - 90
        elif exif_orientation == 8:
            # 图像已经逆时针旋转90°，需要顺时针旋转90°来纠正
            # 即逆时针旋转-90°，所以需要加上90°
            result = calculated_angle + 90
        elif exif_orientation == 3:
            # 图像已经旋转180°，需要再旋转180°来纠正
            result = calculated_angle + 180
        else:
            # 方向1或其他，不需要调整
            result = calculated_angle
        
        return result % 360
    
    def compute_geo_boundary(self, metadata, rotation_angle):
        """
        计算旋转后图像的地理边界
        
        参数说明：
        - rotation_angle: 逆时针旋转角度（与图像旋转角度一致）
        - 地理边界应该基于旋转后的图像计算
        
        注意：这里使用逆时针角度，与图像旋转保持一致
        """
        if not all([metadata.get("GPSLatitude"), metadata.get("GPSLongitude"), 
                    metadata.get("AbsoluteAltitude"), metadata.get("width"), 
                    metadata.get("height")]):
            logger.warning("缺少必要的元数据来计算地理边界")
            return None
        
        lat = metadata["GPSLatitude"]
        lon = metadata["GPSLongitude"]
        alt = metadata["AbsoluteAltitude"]
        width = metadata["width"]
        height = metadata["height"]
        
        earth_radius = 6378137.0
        sensor_width = 35
        focal_length = 24.0
        ground_resolution = (sensor_width * alt) / (focal_length * width)
        
        half_width = width * ground_resolution / 2
        half_height = height * ground_resolution / 2
        
        # rotation_angle是逆时针角度，直接使用
        # 旋转矩阵：逆时针旋转角度
        radian_azimuth = rotation_angle * math.pi / 180
        cos_a = math.cos(radian_azimuth)
        sin_a = math.sin(radian_azimuth)
        
        # 定义四个角点（以图像中心为原点，E=东，N=北）
        # 注意：这里假设图像的上边是北，右边是东
        corners = [
            {"E": half_width, "N": half_height},      # 右上（东北）
            {"E": -half_width, "N": half_height},     # 左上（西北）
            {"E": -half_width, "N": -half_height},    # 左下（西南）
            {"E": half_width, "N": -half_height}      # 右下（东南）
        ]
        
        lons = []
        lats = []
        
        # 旋转角点坐标（逆时针旋转）
        for corner in corners:
            # 旋转矩阵：逆时针旋转
            rotated_e = corner["E"] * cos_a - corner["N"] * sin_a
            rotated_n = corner["E"] * sin_a + corner["N"] * cos_a
            
            # 转换为经纬度偏移
            d_lon = rotated_e / (earth_radius * math.cos(lat * math.pi / 180)) * (180 / math.pi)
            d_lat = rotated_n / earth_radius * (180 / math.pi)
            
            lons.append(lon + d_lon)
            lats.append(lat + d_lat)
        
        geo_boundary = {
            "xmin": min(lons),
            "xmax": max(lons),
            "ymin": min(lats),
            "ymax": max(lats),
            "corners": [{"lon": lons[i], "lat": lats[i]} for i in range(4)]
        }
        
        logger.info(f"地理边界计算完成:")
        logger.info(f"  中心坐标: ({lat:.6f}, {lon:.6f})")
        logger.info(f"  高度: {alt:.1f}m")
        logger.info(f"  旋转角度（逆时针）: {rotation_angle:.1f}°")
        logger.info(f"  地面分辨率: {ground_resolution:.3f}m/pixel")
        logger.info(f"  覆盖范围: {half_width*2:.1f}m x {half_height*2:.1f}m")
        
        return geo_boundary
    
    def rotate_image_to_north(self, image_path, rotation_angle, output_path):
        """将图像旋转到正北向，保持透明背景"""
        try:
            img = Image.open(image_path)
            
            if img.mode != 'RGBA':
                img = img.convert('RGBA')
            
            original_width = img.width
            original_height = img.height
            
            radians = rotation_angle * math.pi / 180
            
            new_width = int(
                original_width * abs(math.cos(radians)) + 
                original_height * abs(math.sin(radians))
            )
            new_height = int(
                original_height * abs(math.cos(radians)) + 
                original_width * abs(math.sin(radians))
            )
            
            rotated_img = Image.new('RGBA', (new_width, new_height), (0, 0, 0, 0))
            temp_rotated = img.rotate(rotation_angle, expand=True, fillcolor=(0, 0, 0, 0))
            
            paste_x = (new_width - temp_rotated.width) // 2
            paste_y = (new_height - temp_rotated.height) // 2
            
            paste_x = max(0, paste_x)
            paste_y = max(0, paste_y)
            
            rotated_img.paste(temp_rotated, (paste_x, paste_y), temp_rotated)
            rotated_img.save(output_path, 'PNG', optimize=True)
            
            logger.info(f"图像旋转完成: {os.path.basename(output_path)}")
            return True
            
        except Exception as e:
            logger.error(f"旋转图像失败: {e}")
            return False
    
    def compress_png_with_pixel_density(self, input_path, output_path, target_ratio=None):
        """使用像素密度降低的方法压缩PNG图片"""
        if target_ratio is None:
            target_ratio = self.target_ratio
            
        try:
            img = Image.open(input_path)
            original_size = os.path.getsize(input_path)
            target_size = original_size / target_ratio
            
            if img.mode != 'RGBA':
                img = img.convert('RGBA')
            
            original_width = img.width
            original_height = img.height
            original_pixels = original_width * original_height
            
            scale_levels = [0.1, 0.08, 0.05, 0.03, 0.02, 0.01]
            
            best_img = img
            best_size = original_size
            best_scale = 1.0
            
            for scale in scale_levels:
                try:
                    low_res_width = max(1, int(original_width * scale))
                    low_res_height = max(1, int(original_height * scale))
                    
                    low_res_img = img.resize((low_res_width, low_res_height), Image.Resampling.LANCZOS)
                    pixelated_img = low_res_img.resize((original_width, original_height), Image.Resampling.NEAREST)
                    
                    buffer = io.BytesIO()
                    pixelated_img.save(buffer, format='PNG', optimize=True, compress_level=9)
                    current_size = len(buffer.getvalue())
                    
                    if current_size <= target_size:
                        best_img = pixelated_img
                        best_size = current_size
                        best_scale = scale
                        break
                    
                    if current_size < best_size:
                        best_img = pixelated_img
                        best_size = current_size
                        best_scale = scale
                        
                except Exception as e:
                    logger.warning(f"密度 {scale:.2f} 处理失败: {e}")
                    continue
            
            if best_img.size != (original_width, original_height):
                best_img = best_img.resize((original_width, original_height), Image.Resampling.NEAREST)
            
            best_img.save(output_path, 'PNG', optimize=True, compress_level=9)
            
            final_size = os.path.getsize(output_path)
            compression_ratio = original_size / final_size
            
            logger.info(f"像素密度压缩完成: {os.path.basename(input_path)}")
            logger.info(f"  使用缩放级别: {best_scale:.2f}")
            logger.info(f"  压缩比: {compression_ratio:.1f}:1")
            logger.info(f"  文件大小: {original_size/1024/1024:.2f}MB -> {final_size/1024/1024:.2f}MB")
            
            return True
            
        except Exception as e:
            logger.error(f"像素密度压缩失败 {input_path}: {e}")
            return False
    
    def detect_objects(self, image_path, output_dir, image_name):
        """使用YOLO进行目标检测"""
        if not self.yolo_model:
            logger.error("YOLO模型未初始化")
            return None, []
        
        try:
            # 创建以影像名命名的文件夹
            image_output_dir = os.path.join(output_dir, image_name)
            
            # 运行YOLO检测，使用影像名作为文件夹名
            results = self.yolo_model.predict(
                source=image_path, 
                save=True, 
                save_txt=True, 
                conf=0.5,
                project=image_output_dir,
                name="."
            )
            
            # 解析检测结果
            detections = []
            if results and len(results) > 0:
                result = results[0]
                if result.boxes is not None:
                    for box in result.boxes:
                        class_id = int(box.cls[0])
                        confidence = float(box.conf[0])
                        x, y, w, h = box.xywh[0].tolist()
                        
                        # 转换为相对坐标
                        img_width, img_height = result.orig_shape
                        x_center = x / img_width
                        y_center = y / img_height
                        width_rel = w / img_width
                        height_rel = h / img_height
                        
                        detection = {
                            "class_id": class_id,
                            "class_name": self.class_names.get(class_id, f"class_{class_id}"),
                            "confidence": round(confidence, 6),  # 限制精度
                            "x_center": round(x_center, 6),
                            "y_center": round(y_center, 6),
                            "width": round(width_rel, 6),
                            "height": round(height_rel, 6)
                        }
                        detections.append(detection)
            
            logger.info(f"检测到 {len(detections)} 个目标")
            return results, detections
            
        except Exception as e:
            logger.error(f"YOLO检测失败: {e}")
            return None, []
    
    
    
    def generate_mars3d_data(self, image_name, metadata, geo_boundary, detections):
        """生成Mars3D可视化所需的JSON数据"""
        try:
            # 简化Mars3D数据，只包含必要信息，避免数据冗余
            mars3d_data = {
                "name": image_name,
                "type": "detected" if detections else "normal",
                "imageUrl": "",  # 将在上传后更新
                "format": "png",
                "transparent": True,
                "compressed_method": "pixel_density",
                "extent": geo_boundary if geo_boundary else None,
                "corners": geo_boundary.get("corners", []) if geo_boundary else [],
                "center": {
                    "lon": metadata.get("GPSLongitude"),
                    "lat": metadata.get("GPSLatitude"),
                    "alt": metadata.get("AbsoluteAltitude")
                },
                "rotation": metadata.get("rotation_angle", 0),
                "detections": detections,
                "timestamp": datetime.now().isoformat()
            }
            
            logger.info(f"Mars3D数据生成完成: {image_name}")
            return mars3d_data
            
        except Exception as e:
            logger.error(f"生成Mars3D数据失败: {e}")
            return None

    def save_data_to_json(self, image_output_dir, image_name, image_url, detections, metadata, geo_boundary, mars3d_data, upload_result):
        """保存所有数据到JSON文件"""
        try:
            # 获取压缩图像文件大小
            compressed_path = os.path.join(image_output_dir, f"{image_name}_compressed.png")
            file_size = 0
            if os.path.exists(compressed_path):
                file_size = os.path.getsize(compressed_path)
            
            json_data = {
                "image_info": {
                    "image_name": image_name,
                    "image_url": image_url,
                    "width": metadata.get("width", 0),
                    "height": metadata.get("height", 0),
                    "file_size": file_size,
                    "timestamp": datetime.now().isoformat()
                },
                "detections": detections,
                "metadata": metadata,
                "geo_boundary": geo_boundary,
                "mars3d_data": mars3d_data,
                "upload_info": {
                    "api_url": self.api_url,
                    "upload_time": datetime.now().isoformat(),
                    "upload_success": upload_result is not None,
                    "response": upload_result
                }
            }
            
            json_path = os.path.join(image_output_dir, f"{image_name}_data.json")
            with open(json_path, 'w', encoding='utf-8') as f:
                json.dump(json_data, f, ensure_ascii=False, indent=2)
            
            logger.info(f"数据已保存到JSON文件: {json_path}")
            return json_path
            
        except Exception as e:
            logger.error(f"保存JSON数据失败: {e}")
            return None

    def upload_to_api(self, image_path, detections, metadata, geo_boundary, mars3d_data=None):
        """上传数据到API"""
        try:
            # 准备上传数据
            with open(image_path, 'rb') as f:
                image_data = f.read()
            
            # 构建请求数据
            try:
                # 清理数据，确保JSON序列化安全
                def clean_for_json(data):
                    """清理数据，确保JSON序列化安全"""
                    if data is None:
                        return None
                    elif isinstance(data, (int, float)):
                        # 处理特殊浮点数值
                        if isinstance(data, float):
                            if data != data:  # NaN
                                return 0.0
                            if data == float('inf'):
                                return 999999.0
                            if data == float('-inf'):
                                return -999999.0
                        return data
                    elif isinstance(data, str):
                        # 清理字符串中的控制字符
                        return data.replace('\n', ' ').replace('\r', ' ').replace('\t', ' ').replace('\0', '')
                    elif isinstance(data, dict):
                        return {str(k): clean_for_json(v) for k, v in data.items()}
                    elif isinstance(data, list):
                        return [clean_for_json(item) for item in data]
                    else:
                        return str(data)
                
                # 清理所有数据
                clean_detections = clean_for_json(detections)
                clean_metadata = clean_for_json(metadata)
                clean_geo_boundary = clean_for_json(geo_boundary)
                clean_mars3d_data = clean_for_json(mars3d_data)
                
                detections_json = json.dumps(clean_detections, ensure_ascii=False) if clean_detections else None
                metadata_json = json.dumps(clean_metadata, ensure_ascii=False) if clean_metadata else None
                geo_boundary_json = json.dumps(clean_geo_boundary, ensure_ascii=False) if clean_geo_boundary else None
                mars3d_data_json = json.dumps(clean_mars3d_data, ensure_ascii=False) if clean_mars3d_data else None
                
                logger.info(f"JSON序列化成功 - detections: {len(detections_json) if detections_json else 0} 字符")
                logger.info(f"JSON序列化成功 - metadata: {len(metadata_json) if metadata_json else 0} 字符")
                logger.info(f"JSON序列化成功 - geo_boundary: {len(geo_boundary_json) if geo_boundary_json else 0} 字符")
                logger.info(f"JSON序列化成功 - mars3d_data: {len(mars3d_data_json) if mars3d_data_json else 0} 字符")
                
                # 输出实际的JSON内容用于调试
                if detections_json:
                    logger.info(f"detections JSON内容: {detections_json}")
                if metadata_json:
                    logger.info(f"metadata JSON内容前200字符: {metadata_json[:200]}...")
                if geo_boundary_json:
                    logger.info(f"geo_boundary JSON内容: {geo_boundary_json}")
                if mars3d_data_json:
                    logger.info(f"mars3d_data JSON内容前200字符: {mars3d_data_json[:200]}...")
                
                # 验证JSON格式
                if detections_json:
                    json.loads(detections_json)  # 验证JSON格式
                if metadata_json:
                    json.loads(metadata_json)
                if geo_boundary_json:
                    json.loads(geo_boundary_json)
                if mars3d_data_json:
                    json.loads(mars3d_data_json)
                
                logger.info("所有JSON数据格式验证通过")
                
            except Exception as json_err:
                logger.error(f"JSON序列化失败: {json_err}")
                logger.error(f"detections类型: {type(detections)}, 内容: {detections}")
                logger.error(f"metadata类型: {type(metadata)}, 内容: {metadata}")
                logger.error(f"geo_boundary类型: {type(geo_boundary)}, 内容: {geo_boundary}")
                logger.error(f"mars3d_data类型: {type(mars3d_data)}, 内容: {mars3d_data}")
                return None
            
            data = {
                "device_token": self.device_token,
                "image_name": os.path.basename(image_path),
                "width": metadata.get("width", 0),
                "height": metadata.get("height", 0),
                "file_size": len(image_data),
                "detections": detections_json,
                "metadata": metadata_json,
                "geo_boundary": geo_boundary_json,
                "mars3d_data": mars3d_data_json
            }
            
            files = {
                "image": (os.path.basename(image_path), image_data, "image/png")
            }
            
            logger.info(f"准备发送HTTP请求到: {self.api_url}")
            logger.info(f"请求数据大小: {len(str(data))} 字符")
            logger.info(f"图像文件大小: {len(image_data)} 字节")
            
            # 先测试服务器连接
            try:
                test_response = requests.get("http://localhost:3001/", timeout=5)
                logger.info(f"服务器连接测试成功，状态码: {test_response.status_code}")
            except Exception as test_err:
                logger.error(f"服务器连接测试失败: {test_err}")
                logger.error("请检查后端服务器是否正在运行")
                return None
            
            # 添加超时设置，避免无限等待
            response = requests.post(
                self.api_url, 
                data=data, 
                files=files,
                timeout=30  # 30秒超时
            )
            
            logger.info(f"HTTP响应状态码: {response.status_code}")
            response.raise_for_status()
            
            result = response.json()
            logger.info(f"数据上传成功: {result}")
            return result
            
        except Exception as e:
            logger.error(f"上传数据失败: {e}")
            return None
    
    def process_image(self, source_image_path, output_dir):
        """处理单张图像：检测目标、提取元数据、旋转压缩、上传"""
        try:
            image_name = os.path.splitext(os.path.basename(source_image_path))[0]
            
            logger.info(f"开始处理图像: {image_name}")
            
            # 1. 从原始图像提取元数据
            logger.info("提取元数据...")
            metadata = self.extract_exif_metadata(source_image_path)
            
            # 2. 计算旋转角度
            rotation_angle = self.calculate_rotation_angle(
                metadata.get("FlightYawDegree"),
                metadata.get("GimbalYawDegree"),
                metadata.get("orientation", 1)
            )
            metadata["rotation_angle"] = rotation_angle
            
            # 3. 计算地理边界
            geo_boundary = self.compute_geo_boundary(metadata, rotation_angle)
            if geo_boundary:
                metadata["geo_boundary"] = geo_boundary
            
            # 4. 使用YOLO进行目标检测（保存到影像名文件夹）
            logger.info("进行目标检测...")
            results, detections = self.detect_objects(source_image_path, output_dir, image_name)
            
            if results is None:
                logger.error("目标检测失败，跳过后续处理")
                return False
            
            # 5. YOLO会自动保存检测结果到影像名文件夹下的labels子文件夹
            # 无需手动保存，YOLO的save_txt=True会自动处理
            
            # 6. 获取YOLO检测后保存的图像路径（在影像名文件夹中）
            image_output_dir = os.path.join(output_dir, image_name)
            detected_image_path = os.path.join(image_output_dir, os.path.basename(source_image_path))
            if not os.path.exists(detected_image_path):
                logger.error(f"未找到检测后的图像: {detected_image_path}")
                return False
            
            logger.info(f"找到检测后的图像: {detected_image_path}")
            
            # 7. 对检测后的图像进行旋转到正北向
            logger.info("旋转检测后的图像到正北向...")
            rotated_filename = f"{image_name}_north.png"
            rotated_path = os.path.join(image_output_dir, rotated_filename)
            
            if not self.rotate_image_to_north(detected_image_path, rotation_angle, rotated_path):
                logger.error("图像旋转失败")
                return False
            
            # 8. 压缩旋转后的图像
            logger.info("压缩图像...")
            compressed_filename = f"{image_name}_compressed.png"
            compressed_path = os.path.join(image_output_dir, compressed_filename)
            
            if not self.compress_png_with_pixel_density(rotated_path, compressed_path):
                logger.error("图像压缩失败")
                return False
            
            # 9. 生成Mars3D可视化所需的JSON数据
            logger.info("生成Mars3D可视化数据...")
            mars3d_data = self.generate_mars3d_data(image_name, metadata, geo_boundary, detections)
            
            # 10. 上传到API（包含压缩图像、检测结果、元数据和Mars3D数据）
            logger.info("上传数据到API...")
            upload_result = self.upload_to_api(compressed_path, detections, metadata, geo_boundary, mars3d_data)
            
            # 11. 保存所有数据到本地JSON文件
            logger.info("保存数据到本地JSON文件...")
            image_url = ""
            if upload_result and 'data' in upload_result:
                image_url = upload_result['data'].get('image_url', '')
            
            json_path = self.save_data_to_json(
                image_output_dir, image_name, image_url, 
                detections, metadata, geo_boundary, mars3d_data, upload_result
            )
            
            if upload_result:
                logger.info(f"图像处理完成: {image_name}")
                if json_path:
                    logger.info(f"本地数据已保存: {json_path}")
                return True
            else:
                logger.error(f"图像上传失败: {image_name}")
                return False
                
        except Exception as e:
            logger.error(f"处理图像失败 {source_image_path}: {e}")
            return False
    
    def get_latest_bin_subfolder(self, bin_dir="./bin"):
        """获取bin目录下最新的子文件夹"""
        if not os.path.exists(bin_dir):
            logger.warning(f"bin目录不存在: {bin_dir}")
            return None
        
        subfolders = []
        for item in os.listdir(bin_dir):
            item_path = os.path.join(bin_dir, item)
            if os.path.isdir(item_path):
                # 获取子文件夹的修改时间
                mtime = os.path.getmtime(item_path)
                subfolders.append((item_path, mtime, item))
        
        if not subfolders:
            logger.warning("bin目录下没有找到任何子文件夹")
            return None
        
        # 按修改时间排序，返回最新的子文件夹
        latest_subfolder = max(subfolders, key=lambda x: x[1])
        logger.info(f"找到最新的bin子文件夹: {latest_subfolder[2]} ({latest_subfolder[0]})")
        return latest_subfolder[0]

    def monitor_and_process(self, bin_dir, output_dir, check_interval, process_existing):
        """监控bin目录下最新文件夹并处理新图像"""
        logger.info(f"开始监控bin目录: {bin_dir}")
        logger.info(f"输出目录: {output_dir}")
        logger.info(f"检查间隔: {check_interval}秒")
        logger.info(f"处理现有文件: {'是' if process_existing else '否'}")
        
        # 确保输出目录存在
        os.makedirs(output_dir, exist_ok=True)
        
        # 获取最新的bin子文件夹作为source目录
        current_source_dir = self.get_latest_bin_subfolder(bin_dir)
        if not current_source_dir:
            logger.error("无法找到有效的bin子文件夹")
            return
        
        # 获取初始文件列表
        known_files = set()
        for filename in os.listdir(current_source_dir):
            if any(filename.lower().endswith(ext.lower()) for ext in self.supported_formats):
                known_files.add(filename)
        
        logger.info(f"当前监控目录: {current_source_dir}")
        logger.info(f"初始文件列表: {len(known_files)} 个文件")
        
        # 如果选择处理现有文件，先处理所有W结尾的文件
        if process_existing:
            logger.info("开始处理现有文件...")
            w_files = [f for f in known_files if len(f) > 4 and f[-5].upper() == 'W']
            logger.info(f"找到 {len(w_files)} 个W结尾的现有文件")
            
            for filename in w_files:
                logger.info(f"处理现有W结尾图像: {filename}")
                source_path = os.path.join(current_source_dir, filename)
                success = self.process_image(source_path, output_dir)
                
                if success:
                    logger.info(f"现有图像处理成功: {filename}")
                else:
                    logger.error(f"现有图像处理失败: {filename}")
            
            logger.info("现有文件处理完成，开始监控新文件...")
        
        try:
            while True:
                # 检查是否有新的bin子文件夹
                latest_source_dir = self.get_latest_bin_subfolder(bin_dir)
                if latest_source_dir != current_source_dir:
                    logger.info(f"检测到新的bin子文件夹，切换到: {latest_source_dir}")
                    current_source_dir = latest_source_dir
                    # 重新获取文件列表
                    known_files = set()
                    for filename in os.listdir(current_source_dir):
                        if any(filename.lower().endswith(ext.lower()) for ext in self.supported_formats):
                            known_files.add(filename)
                    logger.info(f"新子文件夹文件列表: {len(known_files)} 个文件")
                
                # 获取当前文件列表
                current_files = set()
                for filename in os.listdir(current_source_dir):
                    if any(filename.lower().endswith(ext.lower()) for ext in self.supported_formats):
                        current_files.add(filename)
                
                # 找出新增文件
                new_files = current_files - known_files
                
                for filename in new_files:
                    # 检查文件名最后一个字符是否为W（不区分大小写）
                    if len(filename) > 4 and filename[-5].upper() == 'W':
                        logger.info(f"发现新的W结尾图像: {filename}")
                        
                        source_path = os.path.join(current_source_dir, filename)
                        
                        # 处理图像（使用默认的predict路径）
                        success = self.process_image(source_path, output_dir)
                        
                        if success:
                            logger.info(f"图像处理成功: {filename}")
                        else:
                            logger.error(f"图像处理失败: {filename}")
                    else:
                        logger.info(f"文件不符合命名规则，跳过: {filename}")
                
                # 更新已知文件列表
                known_files = current_files
                
                # 等待下次检查
                time.sleep(check_interval)
                
        except KeyboardInterrupt:
            logger.info("监控已停止")
    


def main():
    """主函数"""
    # 配置参数
    bin_dir = "./bin"  # bin目录，会自动检测最新文件夹
    output_dir = "./runs/detect"  # 输出目录
    check_interval = 2  # 检查间隔（秒）
    process_existing = True  # 是否处理现有文件（True=先处理现有文件，False=只监控新文件）
    
    # 角度校正参数（度）
    # 如果图片需要再逆时针旋转一点，使用正数（例如：5表示额外逆时针旋转5度）
    # 如果图片需要再顺时针旋转一点，使用负数（例如：-5表示额外顺时针旋转5度）
    angle_correction = 0  # 默认不校正，根据实际情况调整
    
    # 创建处理器实例
    processor = IntegratedImageProcessor(angle_correction=angle_correction)
    
    # 开始监控和处理
    processor.monitor_and_process(bin_dir, output_dir, check_interval, process_existing)


if __name__ == "__main__":
    main()
