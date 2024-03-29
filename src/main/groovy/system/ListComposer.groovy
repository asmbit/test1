package system

import org.zkoss.zk.ui.Component
import org.zkoss.zk.ui.event.Event
import org.zkoss.zk.ui.event.SortEvent
import org.zkoss.zk.ui.select.annotation.Listen
import org.zkoss.zk.ui.select.annotation.Wire
import org.zkoss.zul.Combobox
import org.zkoss.zul.ListModelList
import org.zkoss.zul.Listbox
import org.zkoss.zul.Listheader
import org.zkoss.zul.Paging

abstract class ListComposer<T, S> extends DomainComposer<T, S> {

	@Wire Listbox lst
	@Wire Paging pgn

	protected String title
	protected Map<String, Object> filters = [eq: [], like: [], between: [], gt: [], lt: [], exclusive: [], obligatory: []]
	protected Map<String, Object> filterValues = [:]
	protected String order = 'asc'
	protected String sort

	@Override
	void doAfterCompose(Component comp) throws Exception {
		super.doAfterCompose(comp)
		title = comp.title
		redraw()
	}

	@Listen("onPaging = #pgn")
	void onPaging() {
		redraw()
	}

	@Listen("onSort = listheader")
	void onSort(SortEvent evt) {
		evt.stopPropagation()
		sort = evt.target.value
		order = evt.target.sortDirection == 'ascending' ? 'desc' : 'asc'
		pgn.activePage = 0
		redraw()
	}

	@Listen("onChange = auxheader *; onSelect = auxheader *")
	void onFilterSet(Event evt) {
		filterValues[evt.target.name] = (evt.target instanceof Combobox) ? evt.target.selectedItem?.value : evt.target.value
	}

	@Listen("onOK = auxheader *")
	void onFilterOK(Event evt) {
		pgn.activePage = 0
		redraw()
	}

	@Listen("onClick = #btnRefresh")
	void onRefresh() {
		redraw()
		showNotification(l('info.list.refreshed'))
	}

	@Listen("onClick = #btnClearFilter")
	void onClearFilter() {
		$('auxheader > *').each {
			if (it.hasProperty('value')) it.value = null
		}
		filterValues = [:]
		pgn.activePage = 0
		redraw()
	}

	void redraw() {
		def data = dataService.list(paginationAttributes + extraAttributes, filterCriteria << sortCriteria)
		def n = data.totalCount
		lst.model = new ListModelList(data)
		pgn.totalSize = n
		windowTitle = title ? "${title} ($n)" : ''
		lst.listhead.children.each { Listheader it ->
			if (it.value == sort) {
				it.sortDirection = order == 'asc' ? 'ascending' : 'descending'
			} else {
				it.sortDirection = 'natural'
			}
		}
	}

	Map getPaginationAttributes() {
		return [offset: pgn.activePage * pgn.pageSize, max: pgn.pageSize]
	}

	Map getExtraAttributes() {
		return [readonly: true]
	}

	Closure getFilterCriteria() {
		return {
			def exclusive = false
			filters.obligatory.each { key ->
				def value = getFilterValue(key)
				if (value != null) {
					eq(key, value)
				}
			}
			filters.exclusive.each { key ->
				def value = getFilterValue(key)
				if (!exclusive && value != null) {
					exclusive = true
					eq(key, value)
				}
			}
			if (!exclusive) {
				filters.eq.each { key ->
					def value = getFilterValue(key)
					if (value != null) {
						eq(key, value)
					}
				}
				filters.like.each { key ->
					def value = getFilterValue(key)
					if (value != null) {
						ilike(key, "%$value%")
					}
				}
				filters.gt.each { key ->
					def value = getFilterValue(key)
					if (value != null) {
						gt(key, value)
					}
				}
				filters.lt.each { key ->
					def value = getFilterValue(key)
					if (value != null) {
						lt(key, value)
					}
				}
				filters.between.each { key ->
					def value = getFilterValue(key)
					if (value != null && value.size() == 2) {
						if (value[0] && value[1])
							between(key, value[0], value[1])
						else if (value[0])
							ge(key, value[0])
						else if (value[1])
							le(key, value[1])
					}
				}
			}
		}
	}

	Closure getSortCriteria() {
		if (!sort)
			return {}
		def split = sort.split('\\.')
		if (split.size() == 1) {
			return {
				order(sort, order)
			}
		} else {
			return {
				"${split[0]}" {
					order(split[1], order)
				}
			}
		}
	}

	Object getFilterValue(String name) {
		if (name == null)
			return null
		def result = filterValues[name]
		if (result instanceof String && result.length() == 0)
			return null
		else
			return result
	}
}
