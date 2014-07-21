<#macro footer>
			</div>
		</div>
	</body>
	<script>
		$(document).ready(function(){
			<#if viewId??>
			$('#' + '${viewId}').addClass('active');
			</#if>
		});
	</script>
</html>
</#macro>