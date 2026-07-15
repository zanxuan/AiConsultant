package com.zx.consultant.knowledge.service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.zx.consultant.knowledge.dto.KnowledgeBaseReq;
import com.zx.consultant.knowledge.mapper.KnowledgeBaseMapper;
import com.zx.consultant.knowledge.entity.KnowledgeBase;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zx.consultant.common.exception.BaseException;
import com.zx.consultant.common.result.PageResult;
import com.zx.consultant.knowledge.vo.KnowledgeBaseVO;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.util.StringUtils;
import org.springframework.transaction.annotation.Transactional;


@Service
public class KnowledgeBaseService  extends ServiceImpl<KnowledgeBaseMapper, KnowledgeBase>{


    /**
     * 创建知识库
     * @param request
     * @param userId
     * @return
     */
    public Long create(KnowledgeBaseReq request, Long userId) {
        KnowledgeBase entity = new KnowledgeBase();
        
        // 1. 将 DTO 的属性 (name, description) 拷贝到 Entity
        BeanUtils.copyProperties(request, entity);
        
        // 2. 设置系统后台生成的业务字段
        entity.setUserId(userId);
        entity.setDocumentCount(0); // 初始文档数为 0
        
        // 3. 保存到数据库 (MyBatis-Plus 会自动生成 ID、create_time、update_time)
        this.save(entity);
        
        return entity.getId();
    }

    /**
     * 查询知识库列表
     * @param keyword
     * @param pageNum
     * @param pageSize
     * @param userId
     * @return
     */
    public PageResult<KnowledgeBaseVO> getPage(String keyword, Integer pageNum, Integer pageSize, Long userId) {
        // 1. 构造分页参数
        Page<KnowledgeBase> page = new Page<>(pageNum, pageSize);
        
        // 2. 构造查询条件
        LambdaQueryWrapper<KnowledgeBase> wrapper = new LambdaQueryWrapper<>();
        
        // 强制带上 userId，实现数据隔离：只能查自己的知识库
        wrapper.eq(KnowledgeBase::getUserId, userId);
        
        // 如果输入了关键字，模糊搜索 name 或 description
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(KnowledgeBase::getName, keyword)
                              .or()
                              .like(KnowledgeBase::getDescription, keyword));
        }
        
        // 默认按照创建时间倒序排列 (最新的在最前)
        wrapper.orderByDesc(KnowledgeBase::getCreateTime);
        
        // 3. 执行物理分页查询
        this.page(page, wrapper);
        
        // 4. 将查出来的 List<Entity> 转换成 List<VO>
        List<KnowledgeBaseVO> voList = page.getRecords().stream()
                .map(this::convertToVO)
                .collect(Collectors.toList());
                
        // 5. 借助我们之前封装的快捷方法返回结果
        return PageResult.of(page, voList);
    }


    /**
     * 获取知识库详情
     * @param knowledgeId
     * @return
     */
    public KnowledgeBaseVO getDetail(Long knowledgeId) {
        // 1. 查询数据库
        KnowledgeBase entity = this.getById(knowledgeId);
        if (entity == null) {
            // 实际项目中建议抛出自定义的业务异常，如 BusinessException
            throw new BaseException("知识库不存在");
        }
        
        // 2. 转换为 VO 返回
        return convertToVO(entity);
    }

    /**
     * 修改知识库
     * @param knowledgeId
     * @param request
     * @param userId
     */
    public void update(Long knowledgeId, KnowledgeBaseReq request, Long userId) {
        // 1. 检查是否存在
        KnowledgeBase entity = this.getById(knowledgeId);
        if (entity == null) {
            throw new RuntimeException("知识库不存在");
        }
        
        // 2. 越权校验：只能修改属于自己的知识库
        if (!entity.getUserId().equals(userId)) {
            throw new RuntimeException("越权操作：无权修改他人的知识库");
        }
        
        // 3. 更新字段并保存
        entity.setName(request.getName());
        entity.setDescription(request.getDescription());
        this.updateById(entity); // update_time 会自动更新
    }



    /**
     * 删除知识库
     * @param knowledgeId
     * @param userId
     */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long knowledgeId, Long userId) {
        //TODO 后期可能采用级联删除文档的方式，暂时不实现

        // 核心妙招：将“越权校验”和“是否存在文档的检查”全部拍到 DELETE 的 WHERE 条件里！
        boolean success = this.lambdaUpdate()
                .eq(KnowledgeBase::getId, knowledgeId)    // 1. 定位要删哪条数据
                .eq(KnowledgeBase::getUserId, userId)     // 2. 越权校验：必须是当前用户的数据
                // 3. 关联拦截：确保文档数为 0 或为 null
                .and(wrapper -> wrapper.isNull(KnowledgeBase::getDocumentCount)
                                       .or()
                                       .eq(KnowledgeBase::getDocumentCount, 0))
                .remove(); // 执行底层 DELETE 语句

        // 如果影响行数（success）为 false，说明整条 SQL 一个都没删掉。
        // 这意味着：要么ID根本不存在，要么是黑客跨越权删除，要么该知识库下还有未清空的文档
        if (!success) {
            throw new RuntimeException("删除失败：知识库不存在、无权操作或该知识库下还存在文档！");
        }
    }



    /**
     * 内部私有方法：Entity 转换为 VO
     */
    private KnowledgeBaseVO convertToVO(KnowledgeBase entity) {
        KnowledgeBaseVO vo = new KnowledgeBaseVO();
        BeanUtils.copyProperties(entity, vo);
        return vo;
    }
}
